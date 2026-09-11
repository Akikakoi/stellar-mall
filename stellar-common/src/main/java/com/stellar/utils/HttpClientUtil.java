package com.stellar.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 客户端工具（对齐 sky HttpClientUtil）。
 * 核心用途：Mall → RAG 调用知识库同步接口（带 X-Rag-Sync-Secret 头）。
 * <p>
 * 全局共享单个带连接池的 CloseableHttpClient（进程内复用 TCP 连接），
 * 需要自定义超时的调用方通过 per-request RequestConfig 覆盖，而不是自建客户端。
 */
@Slf4j
public class HttpClientUtil {

    private static final ObjectMapper MAPPER = new JacksonObjectMapper();

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int SOCKET_TIMEOUT_MS  = 30_000; // 同步 RAG 大文档切向量可能慢，给 30s

    private static final PoolingHttpClientConnectionManager CONN_MANAGER = new PoolingHttpClientConnectionManager();

    /**
     * 共享连接池客户端：应用生命周期内复用，JVM 退出时统一关闭。
     */
    private static final CloseableHttpClient SHARED_CLIENT = buildSharedClient();

    static {
        // 关闭钩子里释放连接池（吞异常即可，进程已在退出）
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                SHARED_CLIENT.close();
            } catch (IOException ignored) {
            }
        }, "httpclient-util-shutdown"));
    }

    private static CloseableHttpClient buildSharedClient() {
        CONN_MANAGER.setMaxTotal(50);
        CONN_MANAGER.setDefaultMaxPerRoute(20); // 目前主要打 RAG 单一路由，20 并发足够
        RequestConfig defaultConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                .build();
        return HttpClients.custom()
                .setConnectionManager(CONN_MANAGER)
                .setDefaultRequestConfig(defaultConfig)
                .evictExpiredConnections()
                .evictIdleConnections(60, TimeUnit.SECONDS)
                .build();
    }

    /** 供需要精细控制请求/响应生命周期的调用方复用共享连接池（不要 close 返回的实例）。 */
    public static CloseableHttpClient sharedClient() {
        return SHARED_CLIENT;
    }

    private static String execute(HttpRequestBase req, Map<String, String> headers) throws IOException {
        return execute(req, headers, null);
    }

    /**
     * 用共享连接池执行请求。
     *
     * @param perRequestConfig 可选，按请求覆盖超时（如 LLM 类长接口）
     */
    private static String execute(HttpRequestBase req, Map<String, String> headers,
                                  RequestConfig perRequestConfig) throws IOException {
        if (perRequestConfig != null) {
            req.setConfig(perRequestConfig);
        }
        if (headers != null) {
            headers.forEach(req::addHeader);
        }
        try (CloseableHttpResponse resp = SHARED_CLIENT.execute(req)) {
            int status = resp.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            if (status >= 400) {
                log.warn("[HttpClientUtil] HTTP {} {} => status={}, body={}", req.getMethod(), req.getURI(), status, body);
            }
            return body;
        }
    }

    public static String doGet(String url, Map<String, String> headers) throws IOException {
        return execute(new HttpGet(url), headers);
    }

    public static String doPost(String url, Map<String, String> headers, Object jsonBody) throws IOException {
        HttpPost post = new HttpPost(url);
        post.addHeader("Content-Type", "application/json; charset=utf-8");
        String json = MAPPER.writeValueAsString(jsonBody);
        post.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
        return execute(post, headers);
    }

    public static String doDelete(String url, Map<String, String> headers, Object jsonBody) throws IOException {
        // HTTP DELETE with body —— Apache HttpClient 用 HttpPost + rewrite，或者自定义 HttpEntityEnclosingRequestBase
        HttpPost post = new HttpPost(url) {
            @Override public String getMethod() { return "DELETE"; }
        };
        post.addHeader("Content-Type", "application/json; charset=utf-8");
        String json = MAPPER.writeValueAsString(jsonBody);
        post.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
        return execute(post, headers);
    }
}
