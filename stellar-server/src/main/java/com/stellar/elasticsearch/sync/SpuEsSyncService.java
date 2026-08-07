package com.stellar.elasticsearch.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.elasticsearch.doc.SpuDocument;
import com.stellar.elasticsearch.event.SpuChangedEvent;
import com.stellar.elasticsearch.repo.SpuEsRepository;
import com.stellar.entity.Spu;
import com.stellar.mapper.SpuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
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

    private final SpuEsRepository spuEsRepository;
    private final SpuMapper spuMapper;

    @Value("${stellar.elasticsearch.enabled:false}")
    private boolean esEnabled;

    @Value("${stellar.rag.base-url:http://127.0.0.1:8000}")
    private String ragBaseUrl;

    public SpuEsSyncService(SpuEsRepository spuEsRepository, SpuMapper spuMapper) {
        this.spuEsRepository = spuEsRepository;
        this.spuMapper = spuMapper;
    }

    @Async
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
        spuEsRepository.save(doc);
        log.debug("SPU {} synced to ES", spuId);
    }

    private void syncDelete(Long spuId) {
        spuEsRepository.deleteById(spuId);
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

        // 构建文档
        List<SpuDocument> docs = new ArrayList<>();
        for (int i = 0; i < allSpus.size(); i++) {
            SpuDocument doc = toDocument(allSpus.get(i));
            if (i < vectors.size()) doc.setNameVec(vectors.get(i));
            docs.add(doc);
        }

        spuEsRepository.saveAll(docs);
        log.info("ES rebuild complete: {} docs with embeddings", docs.size());
        return docs.size();
    }

    /** 拼接商品名+副标题作为向量化文本。 */
    private static String buildEmbedText(Spu spu) {
        StringBuilder sb = new StringBuilder(spu.getName());
        if (spu.getSubTitle() != null && !spu.getSubTitle().isEmpty()) {
            sb.append(" ").append(spu.getSubTitle());
        }
        return sb.toString();
    }

    /** 调 rag-backend /api/embed 批量获取向量。 */
    private List<double[]> fetchEmbeddings(List<String> texts) {
        try {
            String body = MAPPER.writeValueAsString(new EmbedRequest(texts));
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
                log.error("Embedding API returned {}", conn.getResponseCode());
                return List.of();
            }
            JsonNode node = MAPPER.readTree(conn.getInputStream());
            List<double[]> result = new ArrayList<>();
            for (JsonNode vec : node.get("embeddings")) {
                double[] arr = new double[vec.size()];
                for (int i = 0; i < vec.size(); i++) arr[i] = vec.get(i).asDouble();
                result.add(arr);
            }
            log.debug("Fetched {} embeddings, dim={}", result.size(),
                    result.isEmpty() ? 0 : result.get(0).length);
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch embeddings: {}", e.getMessage());
            return List.of();
        }
    }

    /** 单条向量化（商品同步时）。 */
    private SpuDocument toDocumentWithEmbedding(Spu spu) {
        SpuDocument doc = toDocument(spu);
        List<double[]> vecs = fetchEmbeddings(List.of(buildEmbedText(spu)));
        if (!vecs.isEmpty()) doc.setNameVec(vecs.get(0));
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
