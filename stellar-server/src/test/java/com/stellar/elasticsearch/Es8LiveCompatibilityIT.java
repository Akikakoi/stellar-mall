package com.stellar.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.stellar.dto.SpuPageQueryDTO;
import com.stellar.elasticsearch.doc.SpuDocument;
import com.stellar.elasticsearch.service.SearchSuggestService;
import com.stellar.elasticsearch.service.SpuSearchService;
import com.stellar.vo.SearchResultVO;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ES 8 实机兼容性验证：需先启动 ES 8.13.4，再以 -Dstellar.es8.it=true 运行。
 * 覆盖 8.x 新客户端下的建索引 mapping、kNN、BM25 精确匹配、聚合、补全纠错。
 */
@SpringBootTest(properties = {
        "stellar.elasticsearch.enabled=true",
        "stellar.elasticsearch.uris=http://127.0.0.1:9200",
        "stellar.elasticsearch.index-prefix=stellar_it",
        "stellar.elasticsearch.vector-dim=8"
})
@EnabledIfSystemProperty(named = "stellar.es8.it", matches = "true")
class Es8LiveCompatibilityIT {

    private static final String INDEX = "stellar_it_spu";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private ElasticsearchClient client;
    @Autowired
    private ElasticsearchOperations esOps;
    @Autowired
    private RestClient restClient;
    @Autowired
    private SpuSearchService searchService;
    @Autowired
    private SearchSuggestService suggestService;

    @Test
    void fullSearchPipelineWorksOnEs8() throws Exception {
        assertEquals("8.13.4", client.info().version().number(), "必须连到 ES 8.13.4");
        String plugins = EntityUtils.toString(restClient.performRequest(
                new Request("GET", "/_cat/plugins?format=json")).getEntity());
        assertTrue(plugins.contains("analysis-ik"), "IK 插件必须已加载: " + plugins);

        JsonNode properties = get("/" + INDEX + "/_mapping").path(INDEX)
                .path("mappings").path("properties");
        assertEquals("keyword", properties.path("name").path("fields").path("keyword").path("type").asText(),
                "name.keyword 子字段必须随索引创建注入");
        assertEquals("dense_vector", properties.path("nameVec").path("type").asText());
        assertEquals(8, properties.path("nameVec").path("dims").asInt());
        assertTrue(properties.path("nameVec").path("index").asBoolean(), "dense_vector 必须 index:true");
        assertEquals("cosine", properties.path("nameVec").path("similarity").asText());
        assertEquals("ik_max_word", properties.path("name").path("analyzer").asText());

        esOps.save(List.of(
                doc(101L, "iPhone 15 手机", "大屏手机", 1L, "5000", vector(1)),
                doc(102L, "手机壳", "透明保护", 2L, "50", vector(2)),
                doc(103L, "无线耳机", "降噪耳机", 2L, "300", vector(1))));
        restClient.performRequest(new Request("POST", "/" + INDEX + "/_refresh"));

        // 关键词路：101 精确同名应排第一；103 与 query 无词面交集，只可能由 kNN 召回
        cacheEmbedding("iPhone 15 手机", vector(1));
        SpuPageQueryDTO keywordQuery = new SpuPageQueryDTO();
        keywordQuery.setName("iPhone 15 手机");
        keywordQuery.setPage(1);
        keywordQuery.setPageSize(10);
        SearchResultVO keywordResult = searchService.searchWithHighlight(keywordQuery);

        assertEquals(2L, keywordResult.getTotal(), "101(BM25) + 103(仅 kNN)");
        List<String> fragments = keywordResult.getHighlights().get(101L);
        assertNotNull(fragments, "BM25 高亮必须生效");
        assertTrue(fragments.get(0).contains("<em class='hl'>") && fragments.get(0).contains("iPhone 15 手机"),
                "高亮片段: " + fragments);
        assertFalse(keywordResult.getHighlights().containsKey(103L));

        // 非关键词路：ES 聚合必须被新 API 正确解析
        SpuPageQueryDTO aggQuery = new SpuPageQueryDTO();
        aggQuery.setPage(1);
        aggQuery.setPageSize(10);
        SearchResultVO aggResult = searchService.searchWithHighlight(aggQuery);
        assertEquals(3L, aggResult.getTotal());
        assertEquals(2, aggResult.getAggregations().getCategories().size(), "两个分类桶");
        assertFalse(aggResult.getAggregations().getPriceRanges().isEmpty(), "价格区间桶不能为空");

        // 补全 / 纠错
        assertTrue(suggestService.suggest("手机").getCompletions().contains("iPhone 15 手机"),
                "match_phrase_prefix 补全必须命中");
        assertDoesNotThrow(() -> suggestService.correct("手机壳"));
    }

    @AfterEach
    void deleteTestIndex() throws Exception {
        try {
            restClient.performRequest(new Request("DELETE", "/" + INDEX));
        } catch (Exception ignored) {
            // 索引不存在时无需处理
        }
    }

    private JsonNode get(String endpoint) throws Exception {
        return JSON.readTree(EntityUtils.toString(restClient.performRequest(new Request("GET", endpoint)).getEntity()));
    }

    @SuppressWarnings("unchecked")
    private void cacheEmbedding(String text, double[] vector) {
        Cache<String, double[]> cache = (Cache<String, double[]>) ReflectionTestUtils.getField(searchService, "embedCache");
        cache.put(text, vector);
    }

    private static double[] vector(int axis) {
        double[] v = new double[8];
        v[axis] = 1.0;
        return v;
    }

    private static SpuDocument doc(Long id, String name, String subTitle,
                                   Long categoryId, String price, double[] vector) {
        return SpuDocument.builder()
                .id(id).name(name).subTitle(subTitle).categoryId(categoryId)
                .categoryName("测试分类" + categoryId)
                .minPrice(new BigDecimal(price)).maxPrice(new BigDecimal(price))
                .saleCount(1).commentCount(1).totalStock(10)
                .isNew(1).isHot(0).sort(1).status(1)
                .createTime("2026-10-29 12:00:00").updateTime("2026-10-29 12:00:00")
                .nameVec(vector)
                .build();
    }
}
