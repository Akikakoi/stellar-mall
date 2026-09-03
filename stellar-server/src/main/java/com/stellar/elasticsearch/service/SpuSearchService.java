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
import java.util.concurrent.ConcurrentHashMap;
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

    /** 语义向量维度（必须与 ES nameVec mapping 一致，见 ElasticsearchConfig）。
     *  默认 1536 = DashScope text-embedding-v2；本地 bge-large-zh-v1.5 为 1024。 */
    @Value("${stellar.elasticsearch.vector-dim:1536}")
    private int vectorDim = 1536;

    private volatile boolean esAvailable = true;

    /** 查询向量本地缓存：相同文本的 embedding 只请求一次（有上限，防无界增长）。 */
    private final Map<String, double[]> embedCache = new ConcurrentHashMap<>();

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
            // 有关键词 → 双路召回（BM25 文本 + 语义向量）+ RRF 融合，见 hybridSearch()。
            // 说明：旧实现把向量分用 functionScore MULTIPLY 乘进 BM25 分——当口语化 query
            // （如“给老人用的大屏手机”）BM25 无任何词命中时，分数为 0，语义分被乘零，
            // 语义检索实际从未生效；且 MULTIPLY 只作用于 BM25 已命中的文档，无法扩展召回。
            return hybridSearch(dto, keyword, page, size);
        }

        builder.withQuery(boolQuery);

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

    /** 调 rag-backend 获取查询向量（带本地缓存）。
     *  维度与 ES mapping 不一致时返回 null——cosineSimilarity 的 query_vector 维度不匹配
     *  会让 script 查询抛 400，进而触发整库降级 MySQL 30 秒，这里必须提前拦截。 */
    private double[] fetchQueryEmbedding(String text) {
        double[] cached = embedCache.get(text);
        if (cached != null) {
            log.debug("embedCache HIT  text={}", text);
            return cached;
        }
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
            if (arr.length != vectorDim) {
                log.warn("Embedding dim mismatch: got {}, expect {} (text='{}'). Semantic search disabled, "
                        + "check stellar.elasticsearch.vector-dim vs rag-backend embedding model.",
                        arr.length, vectorDim, text);
                return null;
            }
            if (embedCache.size() > 512) embedCache.clear(); // 简单上限保护
            embedCache.put(text, arr);
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

    // ==================== 双路召回 + RRF 融合（关键词搜索核心） ====================

    /** 融合候选取回量：BM25 与向量各取前 N 做 RRF，之后内存分页。 */
    private static final int HYBRID_TOP_K = 100;
    /** RRF 常数 k（越大越平滑）。 */
    private static final int RRF_K = 60;
    /** 纯语义候选最低分：cosineSimilarity+1 >= 1.20 即 cos >= 0.20 才算相关（参与 RRF）。
     *  text-embedding-v2 下「口语 query vs 短商品名」的相似度普遍只有 0.2~0.5，
     *  阈值不宜过高（曾用 0.30 导致语义召回被整体误杀，2026-09-03 实测调低）；
     *  同时作为「中文 query 纯语义兜底」的整体门槛（非中文 query 不做语义兜底）。 */
    private static final double VEC_MIN_SCORE = 1.20;

    /**
     * 关键词搜索：BM25 文本召回 + 语义向量召回 双路独立查询，RRF 融合排序。
     * <p>为何双查而非单查 functionScore：语义相关但词面不命中的文档（口语化 query）
     * 只有向量路能召回；RRF 弥合 BM25 与 cosine 两个不同分数量纲。</p>
     * <p>任一路异常不影响另一路（内部捕获，绝不让搜索整体降级 MySQL）；
     * 高亮只来自 BM25 命中文档，纯语义命中文档前端按普通名称展示。</p>
     */
    private SearchResultVO hybridSearch(SpuPageQueryDTO dto, String keyword, int page, int size) {
        double[] queryVec = fetchQueryEmbedding(keyword); // null 时仅 BM25 一路
        List<String> expandedTerms = synonymEngine.expand(keyword);

        // ---------- 路 1：BM25 文本召回（含同义词/精确/短语，带高亮） ----------
        BoolQueryBuilder textBool = QueryBuilders.boolQuery();
        addFilter(textBool, "status", dto.getStatus());
        addFilter(textBool, "isNew", dto.getIsNew());
        addFilter(textBool, "isHot", dto.getIsHot());
        addFilter(textBool, "categoryId", dto.getCategoryId());
        addPriceRange(textBool, dto);
        textBool.must(buildTextQuery(keyword, expandedTerms));

        NativeSearchQueryBuilder bmBuilder = new NativeSearchQueryBuilder()
                .withQuery(textBool)
                .withPageable(PageRequest.of(0, HYBRID_TOP_K))
                .withHighlightBuilder(new HighlightBuilder()
                        .field("name").preTags("<em class='hl'>").postTags("</em>")
                        .fragmentSize(120).numOfFragments(1).noMatchSize(60)
                        .field("subTitle").preTags("<em class='hl'>").postTags("</em>")
                        .fragmentSize(100).numOfFragments(1).noMatchSize(0));

        List<Long> bmIds = new ArrayList<>();
        Map<Long, List<String>> highlightMap = new HashMap<>();
        try {
            SearchHits<SpuDocument> bmHits = esOps.search(bmBuilder.build(), SpuDocument.class);
            for (SearchHit<SpuDocument> hit : bmHits) {
                bmIds.add(hit.getContent().getId());
                List<String> fragments = new ArrayList<>();
                Map<String, List<String>> hl = hit.getHighlightFields();
                if (hl != null) {
                    List<String> nameHl = hl.get("name");
                    if (nameHl != null) fragments.addAll(nameHl);
                    List<String> subHl = hl.get("subTitle");
                    if (subHl != null) fragments.addAll(subHl);
                }
                if (!fragments.isEmpty()) highlightMap.put(hit.getContent().getId(), fragments);
            }
            log.debug("BM25 recall: {} hits for '{}'", bmIds.size(), keyword);
        } catch (Exception e) {
            log.warn("BM25 recall failed (semantic-only): {}", e.getMessage());
        }

        // ---------- 路 2：语义向量召回（过滤后的文档按相似度排序，剔除低分） ----------
        List<Long> vecIds = new ArrayList<>();
        Map<Long, Double> vecScores = new HashMap<>(); // id -> cos+1，供纯语义整体门槛用
        if (queryVec != null) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("query_vector", toFloatArray(queryVec));
                Script script = new Script(ScriptType.INLINE, "painless",
                        "double s = doc.containsKey('nameVec') "
                                + "? (cosineSimilarity(params.query_vector, doc['nameVec']) + 1.0) : 0.0; return s;",
                        params);
                // ⚠️ 结构约束（2026-09-03 实测 ES 7.17.25）：script_score 若直接包
                // bool(filter=[...])（function_score(query=bool(filter), script_score)），
                // 所有文档脚本分会被算成 0（语义排序失效、只按 docid 返回）；
                // 正确写法是把打分子句放 must、过滤条件放同级 filter——两者平级组织。
                BoolQueryBuilder vecQuery = QueryBuilders.boolQuery()
                        .must(QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(),
                                new ScriptScoreFunctionBuilder(script)));
                addFilter(vecQuery, "status", dto.getStatus());
                addFilter(vecQuery, "isNew", dto.getIsNew());
                addFilter(vecQuery, "isHot", dto.getIsHot());
                addFilter(vecQuery, "categoryId", dto.getCategoryId());
                addPriceRange(vecQuery, dto);

                NativeSearchQuery vecQueryNative = new NativeSearchQueryBuilder()
                        .withQuery(vecQuery)
                        .withPageable(PageRequest.of(0, HYBRID_TOP_K))
                        .build();
                SearchHits<SpuDocument> vecHits = esOps.search(vecQueryNative, SpuDocument.class);
                for (SearchHit<SpuDocument> hit : vecHits) {
                    if (hit.getScore() < VEC_MIN_SCORE) continue; // cos < 0.20 视为无关
                    vecIds.add(hit.getContent().getId());
                    vecScores.put(hit.getContent().getId(), (double) hit.getScore());
                }
                log.debug("Vector recall: {} hits for '{}'", vecIds.size(), keyword);
            } catch (Exception e) {
                log.warn("Vector recall failed (BM25-only): {}", e.getMessage());
            }
        }

        // 纯语义召回的整体质量门槛：仅当 BM25 完全零命中、只能靠向量兜底时生效。
        //  ① query 不含中文（纯拉丁/数字/符号，如乱码 qwertyuiop/zzz）→ 不做语义兜底，返回空；
        //  ② 含中文 → 语义 top cos >= 0.20（分 1.20）即视为弱相关可返回（宁滥勿缺）。
        // 依据 2026-09-03 实测：拉丁乱码 top cos 0.21~0.28 与中文弱查询（"给老人用的大屏手机"
        // 对 A60 学生机 0.279）分数重叠，分数阈值无法分离，改用语言判据；
        // 中文乱码（"哈哈哈哈哈"0.16、"发的发的"0.06）天然低于 0.20，无需额外处理。
        if (bmIds.isEmpty() && !vecIds.isEmpty()) {
            double topCos = vecScores.getOrDefault(vecIds.get(0), 0.0) - 1.0;
            if (!containsCjk(keyword) || topCos + 1.0 < VEC_MIN_SCORE) {
                log.debug("Semantic-only query '{}' dropped (cjk={}, top cos={})",
                        keyword, containsCjk(keyword), topCos);
                vecIds.clear();
            }
        }

        // ---------- RRF 融合 + 内存分页 ----------
        List<Long> merged = rrfMerge(bmIds, vecIds);
        int total = merged.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        List<Long> pageIds = merged.subList(from, to);

        List<Spu> spuList = Collections.emptyList();
        if (!pageIds.isEmpty()) {
            List<Spu> byIds = spuMapper.listByIds(pageIds);
            Map<Long, Spu> map = new HashMap<>(byIds.size());
            for (Spu s : byIds) map.put(s.getId(), s);
            List<Spu> ordered = new ArrayList<>(pageIds.size());
            for (Long id : pageIds) {
                Spu s = map.get(id);
                if (s != null) ordered.add(s);
            }
            spuList = ordered;
        }

        SearchResultVO vo = new SearchResultVO();
        vo.setTotal((long) total);
        vo.setRecords(spuList);
        vo.setHighlights(highlightMap);
        vo.setAggregations(mySqlAggregations(dto));
        return vo;
    }

    /** 是否包含 CJK（中文）字符——纯语义兜底仅对中文 query 开放，
     *  拉丁/数字乱码（qwertyuiop 等）不做语义兜底，避免把无关商品兜进结果。 */
    private static boolean containsCjk(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }

    /** BM25 文本查询子句（原关键词 shoulds，注释保留原设计意图）。 */
    private BoolQueryBuilder buildTextQuery(String keyword, List<String> expandedTerms) {
        BoolQueryBuilder q = QueryBuilders.boolQuery();
        // ① 精确字符串匹配 keyword 字段：最高优先级，确保完全同名商品排最前
        q.should(QueryBuilders.termQuery("name.keyword", keyword).boost(100.0f));
        // ② 精确短语匹配：次高优先级
        q.should(QueryBuilders.matchPhraseQuery("name", keyword).boost(20.0f));
        // ③ 分词匹配（无模糊）：原词 best_fields，AND 语义要求所有分词都命中同一字段，
        //    避免 ik 把多字词切成单字后 OR 语义导致的单字误匹配（如搜"钢化膜"命中"模块化拼接"的"化"）
        q.should(QueryBuilders.multiMatchQuery(keyword, "name", "subTitle")
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                .operator(Operator.AND)
                .field("name", 3.0f)
                .field("subTitle", 1.0f));
        // 同义词扩展：权重低于原词。用 match_phrase（短语匹配）而非 multi_match：ik 分词器
        // 会把多字同义词（如"钢化膜"）切成单字（钢/化/膜），multi_match 的 OR 语义让任意单字都能命中，
        // 导致搜"手机膜"时同义词"钢化膜"的单字"化"误命中副标题含"模块化拼接"的无关商品；
        // 短语匹配要求同义词整体连续出现，杜绝单字误匹配。
        for (int i = 1; i < expandedTerms.size(); i++) {
            String syn = expandedTerms.get(i);
            q.should(QueryBuilders.matchPhraseQuery("name", syn).boost(1.5f));
            q.should(QueryBuilders.matchPhraseQuery("subTitle", syn).boost(0.5f));
        }
        q.minimumShouldMatch(1);
        return q;
    }

    /** Reciprocal Rank Fusion：两条有序 id 列表按 rank 加权合并（分数高者优）。 */
    private static List<Long> rrfMerge(List<Long> bmIds, List<Long> vecIds) {
        Map<Long, Double> score = new HashMap<>();
        for (int i = 0; i < bmIds.size(); i++)
            score.merge(bmIds.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        for (int i = 0; i < vecIds.size(); i++)
            score.merge(vecIds.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        return score.entrySet().stream()
                .sorted((x, y) -> Double.compare(y.getValue(), x.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /** 关键词搜索下聚合降级到 MySQL（双路查询不再单独走 ES 聚合）。 */
    private AggregationVO mySqlAggregations(SpuPageQueryDTO dto) {
        AggregationVO aggVO = new AggregationVO();
        aggVO.setCategories(spuMapper.aggCategories(
                dto.getName(), dto.getCategoryId(),
                dto.getStatus(), dto.getIsNew(), dto.getIsHot(),
                dto.getPriceFrom(), dto.getPriceTo()));
        aggVO.setPriceRanges(spuMapper.aggPriceRanges(
                dto.getName(), dto.getCategoryId(),
                dto.getStatus(), dto.getIsNew(), dto.getIsHot(),
                dto.getPriceFrom(), dto.getPriceTo()));
        return aggVO;
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
