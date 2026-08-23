package com.stellar.elasticsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.dto.SpuPageQueryDTO;
import com.stellar.elasticsearch.doc.SpuDocument;
import com.stellar.entity.Spu;
import com.stellar.mapper.SpuMapper;
import com.stellar.result.PageResult;
import com.stellar.vo.AggregationVO;
import com.stellar.vo.BucketVO;
import com.stellar.vo.SearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScriptScoreFunctionBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.range.ParsedRange;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchAggregations;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * SPU 搜索服务 — BM25 关键词 + BGE 语义向量混合搜索，ES 不可用时降级 MySQL。
 */
@Slf4j
@Service
public class SpuSearchService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ScheduledExecutorService RECOVERY_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "es-recovery"); t.setDaemon(true); return t;
            });

    private final ElasticsearchOperations esOps;
    private final SpuMapper spuMapper;
    private final SynonymEngine synonymEngine;

    @Value("${stellar.elasticsearch.index-prefix:stellar}")
    private String indexPrefix;

    @Value("${stellar.rag.base-url:http://127.0.0.1:8000}")
    private String ragBaseUrl;

    private volatile boolean esAvailable = true;

    public SpuSearchService(ElasticsearchOperations esOps, SpuMapper spuMapper,
                            SynonymEngine synonymEngine) {
        this.esOps = esOps; this.spuMapper = spuMapper;
        this.synonymEngine = synonymEngine;
    }

    @PostConstruct void init() { tryRecoverEs(); }

    // ==================== 增强搜索（高亮 + 聚合） ====================

    public SearchResultVO searchWithHighlight(SpuPageQueryDTO dto) {
        if (esAvailable) {
            try { return searchByEs(dto); }
            catch (Exception e) {
                log.warn("ES failed, fallback MySQL: {}", e.getMessage());
                esAvailable = false;
                tryRecoverEs();
            }
        }
        return fallbackSearch(dto);
    }

    /** 保持向后兼容——PageResult 接口沿用原逻辑。 */
    public PageResult search(SpuPageQueryDTO dto) {
        SearchResultVO vo = searchWithHighlight(dto);
        return new PageResult(vo.getTotal(), vo.getRecords());
    }

    // ==================== ES 搜索（核心） ====================

    private SearchResultVO searchByEs(SpuPageQueryDTO dto) {
        int page = dto.getPage() != null && dto.getPage() > 0 ? dto.getPage() : 1;
        int size = dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 20;
        boolean hasKeyword = dto.getName() != null && !dto.getName().trim().isEmpty();
        String keyword = hasKeyword ? dto.getName().trim() : null;

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 过滤条件（filter 上下文，不参与评分）
        addFilter(boolQuery, "categoryId", dto.getCategoryId());
        addFilter(boolQuery, "status", dto.getStatus());
        addFilter(boolQuery, "isNew", dto.getIsNew());
        addFilter(boolQuery, "isHot", dto.getIsHot());
        addPriceRange(boolQuery, dto);

        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder()
                .withPageable(PageRequest.of(page - 1, size));

        if (hasKeyword) {
            // --- 同义词扩展 ---
            List<String> expandedTerms = synonymEngine.expand(keyword);

            // --- 文本搜索子句（should 上下文，参与评分） ---
            // ① 精确字符串匹配 keyword 字段：最高优先级，确保完全同名商品排最前
            boolQuery.should(QueryBuilders.termQuery("name.keyword", keyword).boost(100.0f));
            // ② 精确短语匹配：次高优先级
            boolQuery.should(QueryBuilders.matchPhraseQuery("name", keyword).boost(20.0f));
            // ③ 分词匹配（无模糊）：原词 best_fields，AND 语义要求所有分词都命中同一字段，
            //    避免 ik 把多字词切成单字后 OR 语义导致的单字误匹配（如搜“钢化膜”命中“模块化拼接”的“化”）
            boolQuery.should(QueryBuilders.multiMatchQuery(keyword, "name", "subTitle")
                    .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                    .operator(Operator.AND)
                    .field("name", 3.0f)
                    .field("subTitle", 1.0f));

            // 同义词扩展：权重低于原词
            // 用 match_phrase（短语匹配）而非 multi_match：ik 分词器会把多字同义词
            // （如“钢化膜”）切成单字（钢/化/膜），multi_match 的 OR 语义让任意单字都能命中，
            // 导致搜“手机膜”时同义词“钢化膜”的单字“化”误命中副标题含“模块化拼接”的无关商品。
            // 短语匹配要求同义词整体连续出现，杜绝单字误匹配。
            for (int i = 1; i < expandedTerms.size(); i++) {
                String syn = expandedTerms.get(i);
                boolQuery.should(QueryBuilders.matchPhraseQuery("name", syn).boost(1.5f));
                boolQuery.should(QueryBuilders.matchPhraseQuery("subTitle", syn).boost(0.5f));
            }
            boolQuery.minimumShouldMatch(1);

            // --- 向量语义搜索融合（MULTIPLY 模式：不改变精确匹配排序，仅在同分时微调） ---
            double[] queryVec = fetchQueryEmbedding(keyword);
            if (queryVec != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("query_vector", toFloatArray(queryVec));
                Script script = new Script(ScriptType.INLINE, "painless",
                        "cosineSimilarity(params.query_vector, 'nameVec') + 1.0", params);
                builder.withQuery(QueryBuilders.functionScoreQuery(boolQuery,
                        new ScriptScoreFunctionBuilder(script)).boostMode(
                        org.elasticsearch.common.lucene.search.function.CombineFunction.MULTIPLY));
            } else {
                builder.withQuery(boolQuery);
            }
        } else {
            builder.withQuery(boolQuery);
        }

        // --- 排序：指定字段时按字段排序；综合（null/空）时走 ES 默认评分排序 ---
        if (dto.getSortBy() != null && !dto.getSortBy().trim().isEmpty()) {
            String sortField = resolveSortField(dto.getSortBy().trim());
            if (sortField != null) {
                Sort.Direction dir = "desc".equalsIgnoreCase(dto.getSortOrder())
                        ? Sort.Direction.DESC : Sort.Direction.ASC;
                builder.withSort(Sort.by(dir, sortField));
            }
        }

        // --- 高亮 ---
        builder.withHighlightBuilder(
                new HighlightBuilder()
                        .field("name").preTags("<em class='hl'>").postTags("</em>")
                        .fragmentSize(120).numOfFragments(1).noMatchSize(60)
                        .field("subTitle").preTags("<em class='hl'>").postTags("</em>")
                        .fragmentSize(100).numOfFragments(1).noMatchSize(0));

        // --- 聚合 ---
        builder.withAggregations(
                AggregationBuilders.terms("by_category").field("categoryId").size(20),
                AggregationBuilders.range("by_price")
                        .field("minPrice")
                        .addUnboundedTo(50).addRange(50, 100).addRange(100, 200)
                        .addRange(200, 500).addUnboundedFrom(500));

        NativeSearchQuery query = builder.build();
        SearchHits<SpuDocument> hits = esOps.search(query, SpuDocument.class);

        // --- 提取高亮 ---
        Map<Long, List<String>> highlightMap = new HashMap<>();
        for (SearchHit<SpuDocument> hit : hits) {
            Map<String, List<String>> hlFields = hit.getHighlightFields();
            if (hlFields != null && !hlFields.isEmpty()) {
                List<String> fragments = new ArrayList<>();
                List<String> nameHl = hlFields.get("name");
                if (nameHl != null) fragments.addAll(nameHl);
                List<String> subHl = hlFields.get("subTitle");
                if (subHl != null) fragments.addAll(subHl);
                if (!fragments.isEmpty()) {
                    highlightMap.put(hit.getContent().getId(), fragments);
                }
            }
        }

        // --- 提取聚合 ---
        AggregationVO aggVO = new AggregationVO();
        AggregationsContainer<?> aggContainer = hits.getAggregations();
        org.elasticsearch.search.aggregations.Aggregations esAggs = null;
        if (aggContainer instanceof ElasticsearchAggregations) {
            esAggs = ((ElasticsearchAggregations) aggContainer).aggregations();
        } else if (aggContainer != null) {
            // 尝试通过反射获取底层 ES aggregations（兼容不同版本 Spring Data ES）
            try {
                java.lang.reflect.Method m = aggContainer.getClass().getMethod("aggregations");
                Object result = m.invoke(aggContainer);
                if (result instanceof org.elasticsearch.search.aggregations.Aggregations) {
                    esAggs = (org.elasticsearch.search.aggregations.Aggregations) result;
                }
            } catch (Exception ignored) { }
        }
        if (esAggs != null) {
            // 分类聚合
            Terms catTerms = esAggs.get("by_category");
            if (catTerms != null) {
                List<BucketVO> catBuckets = new ArrayList<>();
                for (Terms.Bucket b : catTerms.getBuckets()) {
                    catBuckets.add(new BucketVO(b.getKeyAsString(), b.getDocCount()));
                }
                aggVO.setCategories(catBuckets);
            }
            // 价格区间聚合
            ParsedRange priceRange = esAggs.get("by_price");
            if (priceRange != null) {
                List<BucketVO> priceBuckets = new ArrayList<>();
                for (Range.Bucket b : priceRange.getBuckets()) {
                    priceBuckets.add(new BucketVO(b.getKeyAsString(), b.getDocCount()));
                }
                aggVO.setPriceRanges(priceBuckets);
            }
        }
        // ES 聚合提取失败时，回退到 MySQL 聚合查询
        if (aggVO.getCategories() == null || aggVO.getCategories().isEmpty()) {
            aggVO.setCategories(spuMapper.aggCategories(
                    dto.getName(), dto.getCategoryId(),
                    dto.getStatus(), dto.getIsNew(), dto.getIsHot(),
                    dto.getPriceFrom(), dto.getPriceTo()));
        }
        if (aggVO.getPriceRanges() == null || aggVO.getPriceRanges().isEmpty()) {
            aggVO.setPriceRanges(spuMapper.aggPriceRanges(
                    dto.getName(), dto.getCategoryId(),
                    dto.getStatus(), dto.getIsNew(), dto.getIsHot(),
                    dto.getPriceFrom(), dto.getPriceTo()));
        }

        // --- 组装结果 ---
        List<Long> ids = hits.getSearchHits().stream()
                .map(SearchHit::getContent).map(SpuDocument::getId).collect(Collectors.toList());
        List<Spu> spuList = ids.isEmpty() ? Collections.emptyList() : spuMapper.listByIds(ids);

        // 按 ES 返回顺序重排（MySQL IN 查询不保证顺序）
        if (!ids.isEmpty() && !spuList.isEmpty()) {
            Map<Long, Spu> spuMap = new HashMap<>(spuList.size());
            for (Spu spu : spuList) {
                spuMap.put(spu.getId(), spu);
            }
            List<Spu> ordered = new ArrayList<>(ids.size());
            for (Long id : ids) {
                Spu spu = spuMap.get(id);
                if (spu != null) ordered.add(spu);
            }
            spuList = ordered;
        }

        SearchResultVO vo = new SearchResultVO();
        vo.setTotal(hits.getTotalHits());
        vo.setRecords(new ArrayList<>(spuList));
        vo.setHighlights(highlightMap);
        vo.setAggregations(aggVO);
        return vo;
    }

    /** 调 rag-backend 获取查询向量。 */
    private double[] fetchQueryEmbedding(String text) {
        try {
            String body = MAPPER.writeValueAsString(Map.of("texts", List.of(text)));
            java.net.URL url = new java.net.URL(ragBaseUrl + "/api/embed");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            conn.getOutputStream().write(bytes);
            conn.getOutputStream().flush();

            if (conn.getResponseCode() != 200) return null;
            JsonNode vec = MAPPER.readTree(conn.getInputStream()).get("embeddings").get(0);
            double[] arr = new double[vec.size()];
            for (int i = 0; i < vec.size(); i++) arr[i] = vec.get(i).asDouble();
            return arr;
        } catch (Exception e) { log.warn("Embedding query failed: {}", e.getMessage()); return null; }
    }

    private static List<Float> toFloatArray(double[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (double v : arr) list.add((float) v);
        return list;
    }

    private void addFilter(BoolQueryBuilder q, String field, Object value) {
        if (value != null) q.filter(QueryBuilders.termQuery(field, value));
    }

    private void addPriceRange(BoolQueryBuilder q, SpuPageQueryDTO dto) {
        if (dto.getPriceFrom() != null && dto.getPriceTo() != null)
            q.filter(QueryBuilders.rangeQuery("minPrice").gte(dto.getPriceFrom().doubleValue()).lte(dto.getPriceTo().doubleValue()));
        else if (dto.getPriceFrom() != null)
            q.filter(QueryBuilders.rangeQuery("minPrice").gte(dto.getPriceFrom().doubleValue()));
        else if (dto.getPriceTo() != null)
            q.filter(QueryBuilders.rangeQuery("maxPrice").lte(dto.getPriceTo().doubleValue()));
    }

    /** 排序字段白名单映射，与 MySQL Mapper XML 保持一致。 */
    private String resolveSortField(String sortBy) {
        switch (sortBy) {
            case "minPrice":   return "minPrice";
            case "saleCount":  return "saleCount";
            case "createTime": return "createTime";
            // name 不使用 field sort——ES Text 字段的 .keyword 子字段在 Spring Data Sort
            // 中会被误解析为嵌套属性路径，走 ES 默认相关性评分即可（termQuery boost 100 保证精确匹配排最前）
            case "name":       return null;
            default:           return null;
        }
    }

    // ==================== MySQL 降级 ====================

    private SearchResultVO fallbackSearch(SpuPageQueryDTO dto) {
        PageResult pageResult = fallbackToMySql(dto);
        SearchResultVO vo = new SearchResultVO();
        vo.setTotal(pageResult.getTotal());
        vo.setRecords(pageResult.getRecords());
        vo.setHighlights(new HashMap<>());
        // MySQL 聚合：不再返回空 AggregationVO，让侧栏有数据
        AggregationVO aggVO = new AggregationVO();
        aggVO.setCategories(spuMapper.aggCategories(
                dto.getName(), dto.getCategoryId(),
                dto.getStatus(), dto.getIsNew(), dto.getIsHot(),
                dto.getPriceFrom(), dto.getPriceTo()));
        aggVO.setPriceRanges(spuMapper.aggPriceRanges(
                dto.getName(), dto.getCategoryId(),
                dto.getStatus(), dto.getIsNew(), dto.getIsHot(),
                dto.getPriceFrom(), dto.getPriceTo()));
        vo.setAggregations(aggVO);
        return vo;
    }

    private PageResult fallbackToMySql(SpuPageQueryDTO dto) {
        int page = dto.getPage() != null && dto.getPage() > 0 ? dto.getPage() : 1;
        int size = dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 20;
        int offset = (page - 1) * size;

        // 有搜索词时按相关性排序，否则走默认排序
        boolean hasKeyword = dto.getName() != null && !dto.getName().trim().isEmpty();
        String sortBy = hasKeyword ? "relevance" : dto.getSortBy();
        String sortOrder = hasKeyword ? null : dto.getSortOrder();

        long total = spuMapper.count(dto.getName(), dto.getCategoryId(),
                dto.getStatus(), dto.getIsNew(), dto.getIsHot(), dto.getPriceFrom(), dto.getPriceTo());
        List<Spu> records = spuMapper.page(offset, size, dto.getName(), dto.getCategoryId(),
                dto.getStatus(), dto.getIsNew(), dto.getIsHot(), dto.getPriceFrom(), dto.getPriceTo(),
                sortBy, sortOrder);
        return new PageResult(total, records);
    }

    private void tryRecoverEs() {
        RECOVERY_EXECUTOR.schedule(() -> {
            try { esOps.indexOps(SpuDocument.class).exists(); esAvailable = true; log.info("ES recovered"); }
            catch (Exception e) { log.warn("ES retry: {}", e.getMessage()); tryRecoverEs(); }
        }, 30, TimeUnit.SECONDS);
    }
}
