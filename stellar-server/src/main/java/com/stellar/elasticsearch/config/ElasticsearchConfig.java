package com.stellar.elasticsearch.config;

import com.stellar.elasticsearch.doc.SpuDocument;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;

/**
 * Elasticsearch 客户端配置（ES 7.17 + Spring Data ES 4.4.x）。
 * 通过 {@code stellar.elasticsearch.enabled=true} 控制是否启用。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "stellar.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class ElasticsearchConfig extends AbstractElasticsearchConfiguration {

    private static final long DEFAULT_CONNECT_TIMEOUT = 5000L;
    private static final long DEFAULT_SOCKET_TIMEOUT = 60000L;
    private static final String DEFAULT_INDEX_PREFIX = "stellar";

    @Value("${stellar.elasticsearch.uris:http://127.0.0.1:9200}")
    private String uris;

    @Value("${stellar.elasticsearch.connect-timeout:5000}")
    private long connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    @Value("${stellar.elasticsearch.socket-timeout:60000}")
    private long socketTimeout = DEFAULT_SOCKET_TIMEOUT;

    @Value("${stellar.elasticsearch.index-prefix:stellar}")
    private String indexPrefix = DEFAULT_INDEX_PREFIX;

    private final ElasticsearchOperations elasticsearchOperations;

    public ElasticsearchConfig(@Lazy ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    /**
     * SPU 搜索索引名，格式：{prefix}_spu，如 stellar_spu。
     */
    public String spuIndexName() {
        return indexPrefix + "_spu";
    }

    @Override
    public RestHighLevelClient elasticsearchClient() {
        org.elasticsearch.client.RestClientBuilder builder =
                org.elasticsearch.client.RestClient.builder(parseHosts(uris));

        builder.setRequestConfigCallback(requestConfigBuilder ->
                requestConfigBuilder
                        .setConnectTimeout(Math.toIntExact(connectTimeout))
                        .setSocketTimeout(Math.toIntExact(socketTimeout))
        );

        org.elasticsearch.client.RestHighLevelClient client =
                new org.elasticsearch.client.RestHighLevelClient(builder);

        log.info("Elasticsearch client initialized, uris={}", uris);
        return client;
    }

    /**
     * 应用启动后确保 SPU 索引存在（幂等），并添加语义向量 dense_vector 映射。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndex(ApplicationReadyEvent event) {
        try {
            IndexOperations idxOps = elasticsearchOperations.indexOps(SpuDocument.class);
            if (!idxOps.exists()) {
                idxOps.create();
                Document mapping = idxOps.createMapping(SpuDocument.class);
                idxOps.putMapping(mapping);
                log.info("ES index {} created with keyword mapping", spuIndexName());
            } else {
                log.info("ES index {} already exists", spuIndexName());
            }

            // 追加/更新 dense_vector 映射（幂等）
            putDenseVectorMapping();
        } catch (Exception e) {
            log.error("Failed to ensure ES index {}: {}", spuIndexName(), e.getMessage(), e);
        }
    }

    /** 为 nameVec 字段添加 dense_vector 映射（1024 维，cosine 相似度）。 */
    private void putDenseVectorMapping() {
        try {
            org.elasticsearch.client.Request request = new org.elasticsearch.client.Request("PUT",
                    "/" + spuIndexName() + "/_mapping");
                request.setJsonEntity("{"
                        + "\"properties\":{"
                        + "\"nameVec\":{\"type\":\"dense_vector\",\"dims\":1536}"
                        + "}}");
            elasticsearchClient().getLowLevelClient().performRequest(request);
            log.info("dense_vector mapping added to {}", spuIndexName());
        } catch (Exception e) {
            log.warn("Failed to add dense_vector mapping: {}", e.getMessage());
        }
    }

    /**
     * 将逗号分隔的 URIs 解析为 HttpHost[]。
     */
    private org.apache.http.HttpHost[] parseHosts(String uris) {
        String[] parts = uris.split(",");
        org.apache.http.HttpHost[] hosts = new org.apache.http.HttpHost[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String uri = parts[i].trim();
            // 支持 http://host:port 格式
            try {
                java.net.URL url = new java.net.URL(uri);
                hosts[i] = new org.apache.http.HttpHost(
                        url.getHost(),
                        url.getPort() > 0 ? url.getPort() : 9200,
                        url.getProtocol()
                );
            } catch (Exception e) {
                log.warn("Invalid ES URI: {}, fallback to default", uri);
                hosts[i] = new org.apache.http.HttpHost("127.0.0.1", 9200, "http");
            }
        }
        return hosts;
    }
}
