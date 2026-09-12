package com.stellar.elasticsearch.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.config.AsyncExecutorConfig;
import com.stellar.elasticsearch.doc.SpuDocument;
import com.stellar.elasticsearch.event.SpuChangedEvent;
import com.stellar.entity.Spu;
import com.stellar.mapper.SpuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SPU ↔ ES 同步服务（含 BGE 语义向量化）。
 */
@Slf4j
@Service
public class SpuEsSyncService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ElasticsearchOperations esOps;
    private final SpuMapper spuMapper;

    @Value("${stellar.elasticsearch.enabled:false}")
    private boolean esEnabled;

    @Value("${stellar.rag.base-url:http://127.0.0.1:8000}")
    private String ragBaseUrl;

    /** 语义向量维度（必须与 ES dense_vector mapping 一致，见 ElasticsearchConfig）。
     *  默认 1536 = DashScope text-embedding-v2；本地 bge-large-zh-v1.5 为 1024。
     *  维度不符时丢弃向量（宁缺毋滥），避免整批写入 ES 报 400。 */
    @Value("${stellar.elasticsearch.vector-dim:1536}")
    private int vectorDim = 1536;

    /** rag-backend /api/embed 单次最大条数（服务端 Field max_length=100），保守取 50。 */
    private static final int EMBED_BATCH_SIZE = 50;

    public SpuEsSyncService(ElasticsearchOperations esOps, SpuMapper spuMapper) {
        this.esOps = esOps;
        this.spuMapper = spuMapper;
    }

    @Async(AsyncExecutorConfig.ES_SYNC_EXECUTOR)
    @EventListener
    public void onSpuChanged(SpuChangedEvent event) {
        if (!esEnabled) return;
        try {
            if (event.getAction() == SpuChangedEvent.Action.SAVE) {
                syncSave(event.getSpuId());
            } else if (event.getAction() == SpuChangedEvent.Action.DELETE) {
                syncDelete(event.getSpuId());
            }
        } catch (Exception e) {
            log.error("Failed to sync SPU {} to ES: {}", event.getSpuId(), e.getMessage(), e);
        }
    }

    private void syncSave(Long spuId) {
        Spu spu = spuMapper.getById(spuId);
        if (spu == null) { log.warn("SPU {} not found, skip ES sync", spuId); return; }
        SpuDocument doc = toDocumentWithEmbedding(spu);
        esOps.save(doc);
        log.debug("SPU {} synced to ES", spuId);
    }

    private void syncDelete(Long spuId) {
        esOps.delete(String.valueOf(spuId), SpuDocument.class);
        log.debug("SPU {} removed from ES", spuId);
    }

    /**
     * 全量重建：批量向量化后写入 ES。
     */
    public long rebuildAll() {
        if (!esEnabled) { log.warn("ES disabled, skip rebuild"); return 0; }

        log.info("Starting full ES index rebuild with embeddings...");
        List<Spu> allSpus = spuMapper.listAll();
        if (allSpus.isEmpty()) { log.info("No SPU data"); return 0; }

        // 批量向量化
        List<String> texts = allSpus.stream()
                .map(s -> buildEmbedText(s))
                .collect(Collectors.toList());
        List<double[]> vectors = fetchEmbeddings(texts);

        // 构建文档（vectors 与 allSpus 等长，null 槽位表示该商品未取到向量）
        List<SpuDocument> docs = new ArrayList<>();
        for (int i = 0; i < allSpus.size(); i++) {
            SpuDocument doc = toDocument(allSpus.get(i));
            if (vectors.get(i) != null) doc.setNameVec(vectors.get(i));
            docs.add(doc);
        }

        esOps.save(docs);
        log.info("ES rebuild complete: {} docs with embeddings", docs.size());
        return docs.size();
    }

    /** 描述参与向量化的最大长度。DashScope text-embedding-v2 单条上限 2048 token，
     *  name+subTitle+分类已占几十字，描述截 400 字：既留足余量，也避免超长描述稀释语义。 */
    private static final int DESC_MAX_LEN = 400;

    /**
     * 拼接商品多字段作为向量化文本：name + subTitle + 分类名 + 描述摘要。
     * <p>只靠 name+subTitle 时，口语化 query（如"给老人用的手机"）与短商品名的语义距离较远；
     * 分类名与描述里的卖点文字（"大字体""长续航"等）能显著拉近语义匹配。
     * 描述优先用 descriptionMd（markdown 清洗去图片/链接/符号），为空时兜底纯文本 description。</p>
     */
    private static String buildEmbedText(Spu spu) {
        StringBuilder sb = new StringBuilder(spu.getName());
        if (hasText(spu.getSubTitle())) sb.append(" ").append(spu.getSubTitle());
        // 分类名：getById/listAll 均联查 category_name，正常有值
        if (hasText(spu.getCategoryName())) sb.append(" 分类:").append(spu.getCategoryName());
        String desc = cleanMarkdown(spu.getDescriptionMd());
        if (!hasText(desc)) desc = spu.getDescription() == null ? "" : spu.getDescription().trim();
        if (hasText(desc)) {
            if (desc.length() > DESC_MAX_LEN) desc = desc.substring(0, DESC_MAX_LEN);
            sb.append(" ").append(desc);
        }
        return sb.toString();
    }

    /** markdown 转纯文本：去图片/链接/裸 URL/标记符号并压缩空白，避免 URL 等噪音污染向量。 */
    private static String cleanMarkdown(String md) {
        if (md == null || md.isBlank()) return "";
        String s = md;
        s = s.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ");    // 图片 ![alt](url) 整体去掉
        s = s.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1"); // 链接 [text](url) 保留 text
        s = s.replaceAll("https?://\\S+", " ");                // 裸 URL
        s = s.replaceAll("[#*_`>|~]", " ");                    // markdown 标记符号
        return s.replaceAll("\\s+", " ").trim();               // 压缩空白
    }

    private static boolean hasText(String s) {
        return s != null && !s.isEmpty();
    }

    /** 调 rag-backend /api/embed 批量获取向量。
     *  <p>分批调用（单次最多 {@link #EMBED_BATCH_SIZE} 条），任一批失败只影响该批并记日志，
     *  不阻断全量同步；返回结果与 texts 严格对齐，失败/维度不符的槽位为 null。</p>
     */
    private List<double[]> fetchEmbeddings(List<String> texts) {
        List<double[]> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) result.add(null);
        if (texts.isEmpty()) return result;

        int ok = 0;
        for (int start = 0; start < texts.size(); start += EMBED_BATCH_SIZE) {
            int end = Math.min(start + EMBED_BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(start, end);
            try {
                List<double[]> batchVecs = doEmbedBatch(batch);
                for (int j = 0; j < batchVecs.size(); j++) {
                    double[] v = batchVecs.get(j);
                    if (v != null && v.length != vectorDim) {
                        log.warn("Embedding dim mismatch: got {}, expect {} (text index {}). "
                                + "Vector discarded, doc will be keyword-searchable only. "
                                + "Check stellar.elasticsearch.vector-dim vs rag-backend embedding model.",
                                v.length, vectorDim, start + j);
                        continue; // 槽位保持 null
                    }
                    result.set(start + j, v);
                    ok++;
                }
            } catch (Exception e) {
                log.error("Embedding batch [{}, {}) failed: {}", start, end, e.getMessage());
            }
        }
        log.info("Embedding fetched: {}/{} (dim={})", ok, texts.size(), vectorDim);
        return result;
    }

    /** 单批 POST /api/embed。受检异常上抛给调用方（fetchEmbeddings 已 catch 记日志）。 */
    private List<double[]> doEmbedBatch(List<String> texts) throws Exception {
        String body = MAPPER.writeValueAsString(new EmbedRequest(new ArrayList<>(texts)));
        java.net.URL url = new java.net.URL(ragBaseUrl + "/api/embed");
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(60000);
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        conn.getOutputStream().write(bytes);
        conn.getOutputStream().flush();

        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("Embedding API returned " + conn.getResponseCode());
        }
        JsonNode node = MAPPER.readTree(conn.getInputStream());
        List<double[]> result = new ArrayList<>(texts.size());
        for (JsonNode vec : node.get("embeddings")) {
            double[] arr = new double[vec.size()];
            for (int i = 0; i < vec.size(); i++) arr[i] = vec.get(i).asDouble();
            result.add(arr);
        }
        log.debug("Fetched {} embeddings, dim={}", result.size(),
                result.isEmpty() ? 0 : result.get(0).length);
        return result;
    }

    /** 单条向量化（商品同步时），维度不符或失败时返回 null。 */
    private double[] fetchOneEmbedding(String text) {
        List<double[]> vecs = fetchEmbeddings(List.of(text));
        return vecs.isEmpty() ? null : vecs.get(0);
    }

    /** 单条向量化（商品同步时）。 */
    private SpuDocument toDocumentWithEmbedding(Spu spu) {
        SpuDocument doc = toDocument(spu);
        double[] vec = fetchOneEmbedding(buildEmbedText(spu));
        if (vec != null) doc.setNameVec(vec);
        else log.warn("SPU {} embedding unavailable, skip nameVec (keyword-only)", spu.getId());
        return doc;
    }

    static SpuDocument toDocument(Spu spu) {
        return SpuDocument.builder()
                .id(spu.getId()).name(spu.getName()).subTitle(spu.getSubTitle())
                .categoryId(spu.getCategoryId())
                .categoryName(spu.getCategoryName())
                .mainImage(spu.getMainImage()).minPrice(spu.getMinPrice())
                .maxPrice(spu.getMaxPrice()).saleCount(spu.getSaleCount())
                .commentCount(spu.getCommentCount()).totalStock(spu.getTotalStock())
                .isNew(spu.getIsNew()).isHot(spu.getIsHot()).sort(spu.getSort())
                .status(spu.getStatus())
                .createTime(spu.getCreateTime() != null ? spu.getCreateTime().toString() : null)
                .updateTime(spu.getUpdateTime() != null ? spu.getUpdateTime().toString() : null)
                .build();
    }

    /** JSON 请求体（内部类）。 */
    @SuppressWarnings("unused")
    private static class EmbedRequest {
        public List<String> texts;
        EmbedRequest(List<String> texts) { this.texts = texts; }
    }
}
