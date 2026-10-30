package com.stellar.elasticsearch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stellar.elasticsearch.doc.SpuDocument;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Elasticsearch 客户端配置（ES 8.13 + Spring Data ES 5.3.x 新客户端）。
 * 通过 {@code stellar.elasticsearch.enabled=true} 控制是否启用。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "stellar.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class ElasticsearchConfig extends ElasticsearchConfiguration {

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
    private final RestClient restClient;

    /** @Lazy 打破自引用环：基类的 @Bean 方法依赖本配置类实例完成初始化。 */
    public ElasticsearchConfig(@Lazy ElasticsearchOperations elasticsearchOperations, @Lazy RestClient restClient) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.restClient = restClient;
    }

    /** 新客户端只收 host:port（https 需 usingSsl，本地开发均为 http）。 */
    @Override
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo(hosts(uris))
                .withConnectTimeout(Duration.ofMillis(connectTimeout))
                .withSocketTimeout(Duration.ofMillis(socketTimeout))
                .build();
    }

    private static String[] hosts(String uris) {
        return Arrays.stream(uris.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ElasticsearchConfig::hostPort)
                .toArray(String[]::new);
    }

    private static String hostPort(String uri) {
        try {
            URI u = URI.create(uri);
            int port = u.getPort() > 0 ? u.getPort() : 9200;
            return u.getHost() + ":" + port;
        } catch (Exception e) {
            log.warn("Invalid ES URI: {}, fallback to default", uri);
            return "127.0.0.1:9200";
        }
    }

    /**
     * SPU 搜索索引名，格式：{prefix}_spu，如 stellar_spu。
     */
    public String spuIndexName() {
        return indexPrefix + "_spu";
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
                // 告警），语义向量在 doc values 中缺失 → 语义检索恒为 0（2026-09-03 实测定案）。
                // 索引创建必须一次到位：补 nameVec 与 name.keyword 子字段后手动 PUT。
                Document mapping = idxOps.createMapping(SpuDocument.class);
                createIndexWithDenseOptions(mapping);
                log.info("ES index {} created with nameVec(dims={}, index=hnsw, cosine) and name.keyword",
                        spuIndexName(), vectorDim);
            } else {
                log.info("ES index {} already exists", spuIndexName());
            }

            // 新增的 keyword 子字段需要调用 rebuild 重写已有文档后才可检索。
            putSearchMapping();
        } catch (Exception e) {
            log.error("Failed to ensure ES index {}: {}", spuIndexName(), e.getMessage(), e);
        }
    }

    /** 手动创建索引：Spring Data 生成的 mapping 中 nameVec 替换为带 index:true 的 dense_vector、
     *  name 补 keyword 子字段后，用低层客户端一次 PUT 建索引。
     *  <p>为什么必须这样：dense_vector 的 dims/index/similarity 建后不可变，Spring Data
     *  的 create()+putMapping 流程会让 nameVec 以「无 index」形态落库，后续无法补救；
     *  buildTextQuery 的 termQuery("name.keyword") 也依赖该子字段。</p>
     */
    private void createIndexWithDenseOptions(Document mapping) throws Exception {
        Object propsObj = mapping.get("properties");
        if (propsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) propsObj;
            Map<String, Object> nameVec = new LinkedHashMap<>();
            nameVec.put("type", "dense_vector");
            nameVec.put("dims", vectorDim);
            nameVec.put("index", true);
            nameVec.put("similarity", "cosine");
            props.put("nameVec", nameVec);

            if (props.get("name") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nameProps = (Map<String, Object>) props.get("name");
                if (!nameProps.containsKey("fields")) {
                    Map<String, Object> keyword = new LinkedHashMap<>();
                    keyword.put("type", "keyword");
                    keyword.put("ignore_above", 256);
                    nameProps.put("fields", Map.of("keyword", keyword));
                }
            }
        }
        // mapping Document 顶层即 properties（供 PUT /_mapping 用）；
        // 建索引 PUT /{index} 的 body 需要 {"mappings": {...}} 包裹一层
        ObjectMapper om = new ObjectMapper();
        ObjectNode body = om.createObjectNode();
        body.set("mappings", om.readTree(mapping.toJson()));
        Request request = new Request("PUT", "/" + spuIndexName());
        request.setJsonEntity(om.writeValueAsString(body));
        restClient.performRequest(request);
    }

    private void putSearchMapping() {
        try {
            Request request = new Request("PUT", "/" + spuIndexName() + "/_mapping");
            request.setJsonEntity("{"
                    + "\"properties\":{"
                    + "\"name\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},"
                    + "\"nameVec\":{\"type\":\"dense_vector\",\"dims\":" + vectorDim
                    + ",\"index\":true,\"similarity\":\"cosine\"}"
                    + "}}");
            restClient.performRequest(request);
            log.info("Search mapping updated for {} (dims={}, index=hnsw, name.keyword)", spuIndexName(), vectorDim);
        } catch (Exception e) {
            // 索引已存在且参数与配置不一致时 ES 会拒绝修改（dense_vector 参数不可变）。
            // 属预期告警：需删索引后重启重建，或调整配置与现有索引一致。
            log.warn("Failed to add dense_vector mapping to {}: {} (dims={}；若为参数冲突，"
                    + "请删除索引 {} 后重启，确保 nameVec 带 index:true 重建)",
                    spuIndexName(), e.getMessage(), vectorDim, spuIndexName());
        }
    }
}
