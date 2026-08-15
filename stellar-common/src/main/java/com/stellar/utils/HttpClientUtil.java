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
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP 客户端工具（对齐 sky HttpClientUtil）。
 * 核心用途：Mall → RAG 调用知识库同步接口（带 X-Rag-Sync-Secret 头）。
 */
@Slf4j
public class HttpClientUtil {

    private static final ObjectMapper MAPPER = new JacksonObjectMapper();

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int SOCKET_TIMEOUT_MS  = 30_000; // 同步 RAG 大文档切向量可能慢，给 30s

    private static RequestConfig requestConfig() {
        return RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                .build();
    }

    private static String execute(HttpRequestBase req, Map<String, String> headers) throws IOException {
        if (headers != null) {
            headers.forEach(req::addHeader);
        }
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(requestConfig()).build();
             CloseableHttpResponse resp = client.execute(req)) {
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
