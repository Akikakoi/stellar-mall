package com.stellar.elasticsearch.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 调 rag-backend {@code POST /api/embed} 的共用 HTTP 客户端。
 * <p>商品搜索的单条 query 向量化与 ES 全量同步的批量向量化都依赖它，
 * 统一维护超时、JSON 序列化与响应解析。</p>
 */
public final class RagEmbeddingClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RagEmbeddingClient() {
    }

    public static double[] embedOne(String baseUrl, String text,
                                    int connectTimeoutMs, int readTimeoutMs) throws Exception {
        List<double[]> vectors = embedBatch(baseUrl, List.of(text), connectTimeoutMs, readTimeoutMs);
        return vectors.isEmpty() ? null : vectors.get(0);
    }

    public static List<double[]> embedBatch(String baseUrl, List<String> texts,
                                            int connectTimeoutMs, int readTimeoutMs) throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("texts", new ArrayList<>(texts)));
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/embed").openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.getOutputStream().write(bytes);
        conn.getOutputStream().flush();

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new RuntimeException("Embedding API returned " + status);
        }
        JsonNode node = MAPPER.readTree(conn.getInputStream());
        List<double[]> result = new ArrayList<>(texts.size());
        for (JsonNode vec : node.get("embeddings")) {
            double[] arr = new double[vec.size()];
            for (int i = 0; i < vec.size(); i++) {
                arr[i] = vec.get(i).asDouble();
            }
            result.add(arr);
        }
        return result;
    }
}
