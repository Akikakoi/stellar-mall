package com.stellar.elasticsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stellar.dto.SpuPageQueryDTO;
import com.stellar.elasticsearch.doc.SpuDocument;
import com.stellar.entity.Spu;
import com.stellar.mapper.SpuMapper;
import com.stellar.result.PageResult;
import com.stellar.vo.AggregationVO;
import com.stellar.vo.BucketVO;
import com.stellar.vo.SearchResultVO;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.RangeBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * SPU 搜索服务 — BM25 关键词 + kNN 语义向量混合搜索（ES 8.13），ES 不可用时降级 MySQL。
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
    /** 向量路走原生客户端：kNN 搜索节支持显式 k（SDE 5.3 的 KnnQuery 不暴露 k 参数）。 */
    private final ElasticsearchClient esClient;
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

    /** 查询向量本地缓存：相同文本的 embedding 只请求一次。
     *  容量上限交给 Caffeine 的 maximumSize —— 按访问频率（W-TinyLFU）淘汰冷 key，
     *  不再用「超过 512 就整体 clear」的粗暴做法：那样会把正在被高频访问的热 key
     *  连同冷 key 一起清掉，下一次搜索被迫重新走一遍 embedding 请求。 */
    private static final int EMBED_CACHE_MAX = 512;
    private final Cache<String, double[]> embedCache = Caffeine.newBuilder()
            .maximumSize(EMBED_CACHE_MAX)
            .build();

    /** 搜索结果短 TTL 缓存：热门词 60s 窗口内重复搜索不再直打 ES/MySQL
     *  （ES 关键词搜索 = BM25+向量双查 + MySQL 取详情，成本不低）。
     *  直接缓存 VO 引用——searchWithHighlight 全链路只读（Controller 仅做 JSON 序列化），
     *  无 JSON 往返反序列化（records 是裸 List，往返会退化为 LinkedHashMap）。
     *  TTL 60s 内商品变更最多延迟一分钟可见，可接受。
     *  过期由 Caffeine 的 expireAfterWrite 管理：写入时即确定过期点，
     *  冷 key 到点立刻回收，不像原来的惰性过期那样只能靠读命中才发现已过期、白白常驻内存。 */
    private static final long RESULT_CACHE_TTL_SECONDS = 60L;
    private static final int RESULT_CACHE_MAX = 1024;
    private final Cache<String, SearchResultVO> resultCache = Caffeine.newBuilder()
            .maximumSize(RESULT_CACHE_MAX)
            .expireAfterWrite(RESULT_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
            .build();

    public SpuSearchService(ElasticsearchOperations esOps, ElasticsearchClient esClient,
                            SpuMapper spuMapper, SynonymEngine synonymEngine) {
        this.esOps = esOps; this.esClient = esClient;
        this.spuMapper = spuMapper;
        this.synonymEngine = synonymEngine;
    }

    @PostConstruct void init() { tryRecoverEs(); }

    private String spuIndexName() {
        return indexPrefix + "_spu";
    }

    // ==================== 增强搜索（高亮 + 聚合） ====================

    public SearchResultVO searchWithHighlight(SpuPageQueryDTO dto) {
        // ---- 结果缓存命中检查 ----
        String cacheKey = resultCacheKey(dto);
        SearchResultVO cached = resultCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("resultCache HIT  key={}", cacheKey);
            return cached;
        }

        SearchResultVO vo = null;
        if (esAvailable) {
            try { vo = searchByEs(dto); }
            catch (Exception e) {
                log.warn("ES failed, fallback MySQL: {}", e.getMessage());
                esAvailable = false;
                tryRecoverEs();
            }
        }
        if (vo == null) vo = fallbackSearch(dto);

        // ---- 写入缓存（异常不影响搜索主流程） ----
        try {
            resultCache.put(cacheKey, vo);
        } catch (Exception e) {
            log.debug("search result cache put failed: {}", e.getMessage());
        }
        return vo;
    }

    /** 结果缓存 key：覆盖 DTO 全部影响结果的字段（trim 归一化空白）。 */
    private static String resultCacheKey(SpuPageQueryDTO dto) {
        return String.join("|",
                norm(dto.getPage()), norm(dto.getPageSize()), norm(dto.getName()),
                norm(dto.getCategoryId()), norm(dto.getStatus()), norm(dto.getIsNew()),
                norm(dto.getIsHot()), norm(dto.getPriceFrom()), norm(dto.getPriceTo()),
                norm(dto.getSortBy()), norm(dto.getSortOrder()));
    }

    private static String norm(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
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

        List<Query> filters = new ArrayList<>();
        addFilter(filters, "categoryId", dto.getCategoryId());
        addFilter(filters, "status", dto.getStatus());
        addFilter(filters, "isNew", dto.getIsNew());
        addFilter(filters, "isHot", dto.getIsHot());
        addPriceRange(filters, dto);

        // 有关键词 → 双路召回（BM25 文本 + kNN 语义向量）+ RRF 融合，见 hybridSearch()。
        // 说明：旧实现把向量分用 functionScore MULTIPLY 乘进 BM25 分——当口语化 query
        // （如“给老人用的大屏手机”）BM25 无任何词命中时，分数为 0，语义分被乘零，
        // 语义检索实际从未生效；且 MULTIPLY 只作用于 BM25 已命中的文档，无法扩展召回。
        if (hasKeyword) {
            return hybridSearch(dto, keyword, page, size);
        }

        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b.filter(filters)))
                .withPageable(PageRequest.of(page - 1, size))
                .withHighlightQuery(highlightQuery());

        // --- 排序：指定字段时按字段排序；综合（null/空）时走 ES 默认评分排序 ---
        if (dto.getSortBy() != null && !dto.getSortBy().trim().isEmpty()) {
            String sortField = resolveSortField(dto.getSortBy().trim());
            if (sortField != null) {
                Sort.Direction dir = "desc".equalsIgnoreCase(dto.getSortOrder())
                        ? Sort.Direction.DESC : Sort.Direction.ASC;
                builder.withSort(Sort.by(dir, sortField));
            }
        }

        // --- 聚合 ---
        builder.withAggregation("by_category",
                Aggregation.of(a -> a.terms(t -> t.field("categoryId").size(20))));
        builder.withAggregation("by_price",
                Aggregation.of(a -> a.range(r -> r.field("minPrice")
                        .ranges(rng -> rng.to("50"))
                        .ranges(rng -> rng.from("50").to("100"))
                        .ranges(rng -> rng.from("100").to("200"))
                        .ranges(rng -> rng.from("200").to("500"))
                        .ranges(rng -> rng.from("500")))));

        SearchHits<SpuDocument> hits = esOps.search(builder.build(), SpuDocument.class);

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
        if (aggContainer instanceof ElasticsearchAggregations esAggs) {
            for (ElasticsearchAggregation agg : esAggs.aggregations()) {
                Aggregate aggregate = agg.aggregation().getAggregate();
                if ("by_category".equals(agg.aggregation().getName()) && aggregate.isLterms()) {
                    List<BucketVO> catBuckets = new ArrayList<>();
                    for (LongTermsBucket bucket : aggregate.lterms().buckets().array()) {
                        catBuckets.add(new BucketVO(String.valueOf(bucket.key()), bucket.docCount()));
                    }
                    aggVO.setCategories(catBuckets);
                } else if ("by_price".equals(agg.aggregation().getName()) && aggregate.isRange()) {
                    List<BucketVO> priceBuckets = new ArrayList<>();
                    for (RangeBucket bucket : aggregate.range().buckets().array()) {
                        priceBuckets.add(new BucketVO(bucket.key(), bucket.docCount()));
                    }
                    aggVO.setPriceRanges(priceBuckets);
                }
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
     *  维度与 ES mapping 不一致时返回 null——kNN 的 query_vector 维度不匹配
     *  会让查询抛 400，进而触发整库降级 MySQL 30 秒，这里必须提前拦截。 */
    private double[] fetchQueryEmbedding(String text) {
        double[] cached = embedCache.getIfPresent(text);
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
            embedCache.put(text, arr);
            return arr;
        } catch (Exception e) { log.warn("Embedding query failed: {}", e.getMessage()); return null; }
    }

    private static List<Float> toFloatArray(double[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (double v : arr) list.add((float) v);
        return list;
    }

    private static void addFilter(List<Query> filters, String field, Object value) {
        if (value instanceof Long l) {
            filters.add(Query.of(q -> q.term(t -> t.field(field).value(l))));
        } else if (value instanceof Integer i) {
            filters.add(Query.of(q -> q.term(t -> t.field(field).value(i.longValue()))));
        } else if (value != null) {
            filters.add(Query.of(q -> q.term(t -> t.field(field).value(String.valueOf(value)))));
        }
    }

    private static void addPriceRange(List<Query> filters, SpuPageQueryDTO dto) {
        Double from = dto.getPriceFrom() == null ? null : dto.getPriceFrom().doubleValue();
        Double to = dto.getPriceTo() == null ? null : dto.getPriceTo().doubleValue();
        if (from != null && to != null) {
            filters.add(Query.of(q -> q.range(r -> r.field("minPrice").gte(JsonData.of(from)).lte(JsonData.of(to)))));
        } else if (from != null) {
            filters.add(Query.of(q -> q.range(r -> r.field("minPrice").gte(JsonData.of(from)))));
        } else if (to != null) {
            filters.add(Query.of(q -> q.range(r -> r.field("maxPrice").lte(JsonData.of(to)))));
        }
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
    /** kNN 候选扫描量：HNSW 图上多探一些再取前 k，平衡召回率与耗时。 */
    private static final int KNN_NUM_CANDIDATES = 500;
    /** RRF 常数 k（越大越平滑）。 */
    private static final int RRF_K = 60;
    /** 纯语义兜底最低分（BM25 零命中时）：ES 8 的 kNN 在 similarity=cosine 下
     *  _score = (1+cos)/2 ∈ [0,1]，0.60 即 cos >= 0.20 才算相关（参与 RRF）。
     *  text-embedding-v2 下「口语 query vs 短商品名」的相似度普遍只有 0.2~0.5，
     *  阈值不宜过高（曾用 cos 0.30 导致语义召回被整体误杀，2026-09-03 实测调低）；
     *  同时作为「中文 query 纯语义兜底」的整体门槛（非中文 query 不做语义兜底）。 */
    private static final double VEC_MIN_SCORE = 0.60;
    /** BM25 有命中时的向量补充门槛（双层门槛，2026-10-30 实测引入）：
     *  BM25 命中说明词面已相关，向量路只是「补充语义相关但词面不命中」的商品，
     *  此时应从严——实测「降噪耳机」混入空调 cos 0.270，与弱相关商品
     *  （「给老人用的手机」cos 0.260）分数重叠，单一 0.60（cos 0.20）无法区分；
     *  收紧到 cos >= 0.40（score >= 0.70）后跨品类噪声被剔除。
     *  BM25 零命中时向量路是唯一来源，维持 VEC_MIN_SCORE 宽门槛兜底。 */
    private static final double VEC_MIN_SCORE_WITH_BM25 = 0.70;

    /**
     * 关键词搜索：BM25 文本召回 + kNN 语义召回 双路独立查询，RRF 融合排序。
     * <p>为何双查而非单查 functionScore：语义相关但词面不命中的文档（口语化 query）
     * 只有向量路能召回；RRF 弥合 BM25 与 cosine 两个不同分数量纲。</p>
     * <p>任一路异常不影响另一路（内部捕获，绝不让搜索整体降级 MySQL）；
     * 高亮只来自 BM25 命中文档，纯语义命中文档前端按普通名称展示。</p>
     */
    private SearchResultVO hybridSearch(SpuPageQueryDTO dto, String keyword, int page, int size) {
        double[] queryVec = fetchQueryEmbedding(keyword); // null 时仅 BM25 一路
        List<String> expandedTerms = synonymEngine.expand(keyword);

        List<Query> filters = new ArrayList<>();
        addFilter(filters, "status", dto.getStatus());
        addFilter(filters, "isNew", dto.getIsNew());
        addFilter(filters, "isHot", dto.getIsHot());
        addFilter(filters, "categoryId", dto.getCategoryId());
        addPriceRange(filters, dto);

        // ---------- 路 1：BM25 文本召回（含同义词/精确/短语，带高亮） ----------
        NativeQuery bmQuery = NativeQuery.builder()
                .withQuery(buildTextQuery(keyword, expandedTerms, filters))
                .withPageable(PageRequest.of(0, HYBRID_TOP_K))
                .withHighlightQuery(highlightQuery())
                .build();

        List<Long> bmIds = new ArrayList<>();
        Map<Long, List<String>> highlightMap = new HashMap<>();
        try {
            SearchHits<SpuDocument> bmHits = esOps.search(bmQuery, SpuDocument.class);
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

        // ---------- 路 2：kNN 语义召回（filter 预过滤后按相似度取 top k，剔除低分） ----------
        List<Long> vecIds = new ArrayList<>();
        Map<Long, Double> vecScores = new HashMap<>(); // id -> kNN 分 ((1+cos)/2)，供纯语义整体门槛用
        if (queryVec != null) {
            try {
                // 双层门槛：BM25 命中时向量补充从严（防跨品类噪声），零命中时维持宽门槛兜底
                double minScore = bmIds.isEmpty() ? VEC_MIN_SCORE : VEC_MIN_SCORE_WITH_BM25;
                List<Float> qv = toFloatArray(queryVec);
                // knn 搜索节：k 显式取 100，filter 在图遍历阶段预过滤
                SearchResponse<Void> vecResp = esClient.search(s -> s
                                .index(spuIndexName())
                                .knn(k -> k.field("nameVec").queryVector(qv)
                                        .k((long) HYBRID_TOP_K)
                                        .numCandidates((long) KNN_NUM_CANDIDATES)
                                        .filter(filters))
                                .size(HYBRID_TOP_K),
                        Void.class);
                for (Hit<Void> hit : vecResp.hits().hits()) {
                    Double score = hit.score();
                    if (score == null || score < minScore) continue; // 低于当前层门槛视为无关
                    Long id = Long.valueOf(hit.id());
                    vecIds.add(id);
                    vecScores.put(id, score);
                }
                log.debug("Vector recall: {} hits for '{}' (bm25Hits={}, minScore={})",
                        vecIds.size(), keyword, bmIds.size(), minScore);
            } catch (Exception e) {
                log.warn("Vector recall failed (BM25-only): {}", e.getMessage());
            }
        }

        // 纯语义召回的整体质量门槛：仅当 BM25 完全零命中、只能靠向量兜底时生效。
        //  ① query 不含中文（纯拉丁/数字/符号，如乱码 qwertyuiop/zzz）→ 不做语义兜底，返回空；
        //  ② 含中文 → 语义 top 分 >= 0.60（cos >= 0.20）即视为弱相关可返回（宁滥勿缺）。
        // 依据 2026-09-03 实测：拉丁乱码 top cos 0.21~0.28 与中文弱查询（"给老人用的大屏手机"
        // 对 A60 学生机 0.279）分数重叠，分数阈值无法分离，改用语言判据；
        // 中文乱码（"哈哈哈哈哈"0.16、"发的发的"0.06）天然低于 0.20，无需额外处理。
        if (bmIds.isEmpty() && !vecIds.isEmpty()) {
            double topScore = vecScores.getOrDefault(vecIds.get(0), 0.0);
            if (!containsCjk(keyword) || topScore < VEC_MIN_SCORE) {
                log.debug("Semantic-only query '{}' dropped (cjk={}, top score={})",
                        keyword, containsCjk(keyword), topScore);
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

    /** BM25 文本查询子句（同义词/精确/短语）+ 过滤条件。 */
    private static Query buildTextQuery(String keyword, List<String> expandedTerms, List<Query> filters) {
        return Query.of(q -> q.bool(b -> {
            // ① 精确字符串匹配 name.keyword 子字段（建索引时注入，见 ElasticsearchConfig）：
            //    最高优先级，确保完全同名商品排最前
            b.should(s -> s.term(t -> t.field("name.keyword").value(keyword).boost(100.0f)));
            // ② 精确短语匹配：次高优先级
            b.should(s -> s.matchPhrase(mp -> mp.field("name").query(keyword).boost(20.0f)));
            // ③ 分词匹配（无模糊）：原词 best_fields，AND 语义要求所有分词都命中同一字段，
            //    避免 ik 把多字词切成单字后 OR 语义导致的单字误匹配（如搜"钢化膜"命中"模块化拼接"的"化"）
            b.should(s -> s.multiMatch(mm -> mm.query(keyword)
                    .type(TextQueryType.BestFields)
                    .operator(Operator.And)
                    .fields("name^3", "subTitle")));
            // 同义词扩展：权重低于原词。用 match_phrase（短语匹配）而非 multi_match：ik 分词器
            // 会把多字同义词（如"钢化膜"）切成单字（钢/化/膜），multi_match 的 OR 语义让任意单字都能命中，
            // 导致搜"手机膜"时同义词"钢化膜"的单字"化"误命中副标题含"模块化拼接"的无关商品；
            // 短语匹配要求同义词整体连续出现，杜绝单字误匹配。
            for (int i = 1; i < expandedTerms.size(); i++) {
                String syn = expandedTerms.get(i);
                b.should(s -> s.matchPhrase(mp -> mp.field("name").query(syn).boost(1.5f)));
                b.should(s -> s.matchPhrase(mp -> mp.field("subTitle").query(syn).boost(0.5f)));
            }
            b.minimumShouldMatch("1");
            b.filter(filters);
            return b;
        }));
    }

    /** 高亮配置：name/subTitle 统一 <em class='hl'> 标签（前端样式依赖）。 */
    private static HighlightQuery highlightQuery() {
        HighlightField name = new HighlightField("name", HighlightFieldParameters.builder()
                .withPreTags("<em class='hl'>").withPostTags("</em>")
                .withFragmentSize(120).withNumberOfFragments(1).withNoMatchSize(60).build());
        HighlightField subTitle = new HighlightField("subTitle", HighlightFieldParameters.builder()
                .withPreTags("<em class='hl'>").withPostTags("</em>")
                .withFragmentSize(100).withNumberOfFragments(1).withNoMatchSize(0).build());
        return new HighlightQuery(new Highlight(List.of(name, subTitle)), SpuDocument.class);
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
