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

    /** edge ngram 分析器：IK 分词后对每个词生成 1..20 前缀。
     *  解决单字查询召回（IK 把"冰箱"当整词，搜"冰"时词面完全无法命中）。 */
    private static final String EDGE_ANALYZER = "ik_edge";
    /** 创建/更新索引 settings 用：analysis 为非动态配置，存量索引需 reopen 才能生效。 */
    private static final String ANALYSIS_SETTINGS_JSON = "{"
            + "\"index\":{"
            + "\"max_ngram_diff\":20,"
            + "\"analysis\":{"
            + "\"filter\":{\"edge_ngram_1_20\":{\"type\":\"edge_ngram\",\"min_gram\":1,\"max_gram\":20}},"
            + "\"analyzer\":{\"" + EDGE_ANALYZER + "\":{\"type\":\"custom\",\"tokenizer\":\"ik_max_word\","
            + "\"filter\":[\"edge_ngram_1_20\",\"lowercase\"]}}"
            + "}}}";

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
            boolean created = false;
            if (!idxOps.exists()) {
                // ⚠️ 不能用 idxOps.create()+putMapping：Spring Data 会自动把无 @Field 的
                // nameVec(double[]) 也写进 mapping，此时它没有 index 参数；而 dense_vector
                // 的 index/similarity 一旦建立就不可改，之后再 PUT 补参数会被 ES 忽略（仅 299
                // 告警），语义向量在 doc values 中缺失 → 语义检索恒为 0（2026-09-03 实测定案）。
                // 索引创建必须一次到位：补 settings(edge ngram)、nameVec、name.keyword/name.edge 后手动 PUT。
                Document mapping = idxOps.createMapping(SpuDocument.class);
                createIndexWithDenseOptions(mapping);
                created = true;
                log.info("ES index {} created with nameVec(dims={}, index=hnsw, cosine), "
                        + "name.keyword and name.edge(ik_edge)", spuIndexName(), vectorDim);
            } else {
                log.info("ES index {} already exists", spuIndexName());
            }

            ensureSearchFields(created);
        } catch (Exception e) {
            log.error("Failed to ensure ES index {}: {}", spuIndexName(), e.getMessage(), e);
        }
    }

    /** 确保存量索引具备 name.edge（edge ngram 前缀召回）与 categoryName.text 子字段。
     *  <p>analysis 设置是非动态的：存量索引需带 {@code reopen=true} 更新 settings；
     *  新子字段只对之后写入的文档生效，故最后统一 {@code _update_by_query} 原地回填。
     *  <p>幂等：name.edge 已存在时只追加 categoryName.text，不重发 name/nameVec——
     *  ES 对已有字段参数（如 analyzer/dense_vector）不可变，重发出入即 400。</p>
     */
    private void ensureSearchFields(boolean created) {
        try {
            boolean edgeReady = created || hasEdgeField();
            if (!edgeReady) {
                updateAnalysisSettings();
                putMapping(true);
                log.info("Search mapping updated for {} (name.edge / {} analyzer installed)", spuIndexName(), EDGE_ANALYZER);
            } else if (!created) {
                putMapping(false);
                log.info("Search mapping updated for {} (categoryName.text subfield ensured)", spuIndexName());
            }
            reindexInPlace();
        } catch (Exception e) {
            // 索引已存在且参数与配置不一致时 ES 会拒绝修改（dense_vector 参数不可变）。
            // 属预期告警：需删索引后重启重建，或调整配置与现有索引一致。
            log.warn("Failed to update mapping of {}: {}", spuIndexName(), e.getMessage());
        }
    }

    /** 存量索引是否已含 name.edge 子字段（读取失败按"未就绪"处理，走完整补丁流程）。 */
    private boolean hasEdgeField() {
        try {
            Request request = new Request("GET", "/" + spuIndexName() + "/_mapping");
            org.elasticsearch.client.Response response = restClient.performRequest(request);
            if (response == null || response.getEntity() == null) return false;
            ObjectMapper om = new ObjectMapper();
            return om.readTree(response.getEntity().getContent())
                    .path(spuIndexName()).path("mappings").path("properties")
                    .path("name").path("fields").path("edge").has("type");
        } catch (Exception e) {
            return false;
        }
    }

    /** 存量索引追加 analysis 设置：ES 要求非动态设置先 close，reopen=true 由服务端完成开关。 */
    private void updateAnalysisSettings() throws Exception {
        Request request = new Request("PUT", "/" + spuIndexName() + "/_settings?reopen=true");
        request.setJsonEntity(ANALYSIS_SETTINGS_JSON);
        restClient.performRequest(request);
    }

    /** 追加子字段映射。includeEdge=true 时一并补 name（含 keyword/edge 子字段），
     *  仅在 edge 分析器刚安装完成时调用；否则只发 categoryName.text。 */
    private void putMapping(boolean includeEdge) throws Exception {
        ObjectMapper om = new ObjectMapper();
        ObjectNode properties = om.createObjectNode();
        ObjectNode category = properties.putObject("categoryName");
        category.put("type", "keyword");
        ObjectNode categoryText = category.putObject("fields").putObject("text");
        categoryText.put("type", "text");
        categoryText.put("analyzer", "ik_max_word");
        categoryText.put("search_analyzer", "ik_smart");

        if (includeEdge) {
            ObjectNode name = properties.putObject("name");
            name.put("type", "text");
            name.put("analyzer", "ik_max_word");
            name.put("search_analyzer", "ik_smart");
            ObjectNode fields = name.putObject("fields");
            ObjectNode keyword = fields.putObject("keyword");
            keyword.put("type", "keyword");
            keyword.put("ignore_above", 256);
            ObjectNode edge = fields.putObject("edge");
            edge.put("type", "text");
            edge.put("analyzer", EDGE_ANALYZER);
            edge.put("search_analyzer", "ik_smart");
        }

        ObjectNode body = om.createObjectNode();
        body.set("properties", properties);
        Request request = new Request("PUT", "/" + spuIndexName() + "/_mapping");
        request.setJsonEntity(om.writeValueAsString(body));
        restClient.performRequest(request);
    }

    /** 原地重建存量文档以填充新增子字段（不动 nameVec，无重 embedding 成本）。 */
    private void reindexInPlace() throws Exception {
        Request reindex = new Request("POST", "/" + spuIndexName() + "/_update_by_query?conflicts=proceed");
        reindex.setJsonEntity("{\"query\":{\"match_all\":{}}}");
        restClient.performRequest(reindex);
        log.info("SPU docs reindexed in place to populate search subfields");
    }

    /** 手动创建索引：Spring Data 生成的 mapping 中 nameVec 替换为带 index:true 的 dense_vector、
     *  name 补 keyword/edge 子字段，并带上 edge ngram 分析器 settings，用低层客户端一次 PUT 建索引。
     *  <p>为什么必须这样：dense_vector 的 dims/index/similarity 建后不可变，Spring Data
     *  的 create()+putMapping 流程会让 nameVec 以「无 index」形态落库，后续无法补救；
     *  buildTextQuery 的 termQuery("name.keyword") 与单字召回依赖 name.edge 子字段。</p>
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
                Map<String, Object> fields;
                if (nameProps.get("fields") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existing = (Map<String, Object>) nameProps.get("fields");
                    fields = existing;
                } else {
                    fields = new LinkedHashMap<>();
                    nameProps.put("fields", fields);
                }
                Map<String, Object> keyword = new LinkedHashMap<>();
                keyword.put("type", "keyword");
                keyword.put("ignore_above", 256);
                fields.putIfAbsent("keyword", keyword);

                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("type", "text");
                edge.put("analyzer", EDGE_ANALYZER);
                edge.put("search_analyzer", "ik_smart");
                fields.put("edge", edge);
            }
        }
        // mapping Document 顶层即 properties（供 PUT /_mapping 用）；
        // 建索引 PUT /{index} 的 body 需要 {"mappings": {...}} 包裹一层，
        // settings 需带 edge ngram 分析器（否则 mapping 里引用 ik_edge 会报 analyzer 不存在）
        ObjectMapper om = new ObjectMapper();
        ObjectNode body = om.createObjectNode();
        body.set("settings", om.readTree(ANALYSIS_SETTINGS_JSON).path("index"));
        body.set("mappings", om.readTree(mapping.toJson()));
        Request request = new Request("PUT", "/" + spuIndexName());
        request.setJsonEntity(om.writeValueAsString(body));
        restClient.performRequest(request);
    }
}
