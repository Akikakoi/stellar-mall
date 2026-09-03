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

    /** 语义向量维度：必须与 rag-backend /api/embed 实际返回的维度一致。
     *  默认 1536 = DashScope text-embedding-v2（云端优先）；本地 bge-large-zh-v1.5 为 1024。
     *  ⚠️ dense_vector 创建后 dims 不可修改，切换模型需删索引重建（见 rebuildAll）。 */
    @Value("${stellar.elasticsearch.vector-dim:1536}")
    private int vectorDim = 1536;

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
                // ⚠️ 不能用 idxOps.create()+putMapping：Spring Data 会自动把无 @Field 的
                // nameVec(double[]) 也写进 mapping，此时它没有 index 参数；而 dense_vector
                // 的 index/similarity 一旦建立就不可改，之后再 PUT 补参数会被 ES 忽略（仅 299
                // 告警），语义向量在 doc values 中缺失 → script_score 的 cos 恒为 0（2026-09-03
                // 实测定案）。因此索引创建必须一次到位：注入 index:true 后手动 PUT。
                Document mapping = idxOps.createMapping(SpuDocument.class);
                createIndexWithDenseOptions(mapping);
                log.info("ES index {} created with nameVec(dims={}, index=hnsw, cosine)",
                        spuIndexName(), vectorDim);
            } else {
                log.info("ES index {} already exists", spuIndexName());
            }

            // 兜底：当 nameVec 字段在已存在索引中缺失时补充；字段已存在时 ES 忽略并 299 告警
            // （日志会提示删除索引后重启重建，确保 nameVec 带 index:true）
            putDenseVectorMapping();
        } catch (Exception e) {
            log.error("Failed to ensure ES index {}: {}", spuIndexName(), e.getMessage(), e);
        }
    }

    /** 手动创建索引：把 Spring Data 生成的 mapping 中 nameVec 替换为带 index:true 的
     *  dense_vector 定义后，用 low-level client 一次 PUT 建索引。
     *  <p>为什么必须这样：dense_vector 的 dims/index/similarity 建后不可变，Spring Data
     *  的 create()+putMapping 流程会让 nameVec 以「无 index」形态落库，后续无法补救。</p>
     */
    private void createIndexWithDenseOptions(Document mapping) throws Exception {
        Object propsObj = mapping.get("properties");
        if (propsObj instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> props = (java.util.Map<String, Object>) propsObj;
            java.util.Map<String, Object> nameVec = new java.util.LinkedHashMap<>();
            nameVec.put("type", "dense_vector");
            nameVec.put("dims", vectorDim);
            nameVec.put("index", true);
            nameVec.put("similarity", "cosine");
            props.put("nameVec", nameVec);
        }
        // Spring Data 的 mapping Document 顶层即 properties（供 PUT /_mapping 用）；
        // 建索引 PUT /{index} 的 body 需要 {"mappings": {...}} 包裹一层
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode body = om.createObjectNode();
        body.set("mappings", om.readTree(mapping.toJson()));
        org.elasticsearch.client.Request request = new org.elasticsearch.client.Request("PUT", "/" + spuIndexName());
        request.setJsonEntity(om.writeValueAsString(body));
        elasticsearchClient().getLowLevelClient().performRequest(request);
    }

    /** 为 nameVec 字段添加 dense_vector 映射（仅当字段缺失时生效；字段已存在时被 ES 忽略）。 */
    private void putDenseVectorMapping() {
        try {
            org.elasticsearch.client.Request request = new org.elasticsearch.client.Request("PUT",
                    "/" + spuIndexName() + "/_mapping");
            request.setJsonEntity("{"
                    + "\"properties\":{"
                    + "\"nameVec\":{\"type\":\"dense_vector\",\"dims\":" + vectorDim
                    + ",\"index\":true,\"similarity\":\"cosine\"}"
                    + "}}");
            elasticsearchClient().getLowLevelClient().performRequest(request);
            log.info("dense_vector mapping added to {} (dims={}, index=hnsw)", spuIndexName(), vectorDim);
        } catch (Exception e) {
            // 索引已存在且参数与配置不一致时 ES 会拒绝修改（dense_vector 参数不可变）。
            // 属预期告警：需删索引后重启重建，或调整配置与现有索引一致。
            log.warn("Failed to add dense_vector mapping to {}: {} (dims={}；若为参数冲突，"
                    + "请删除索引 {} 后重启，确保 nameVec 带 index:true 重建)",
                    spuIndexName(), e.getMessage(), vectorDim, spuIndexName());
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
