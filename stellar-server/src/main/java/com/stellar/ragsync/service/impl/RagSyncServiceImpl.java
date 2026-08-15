package com.stellar.ragsync.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.json.JacksonObjectMapper;
import com.stellar.entity.RagSyncOutbox;
import com.stellar.entity.Spu;
import com.stellar.ragsync.client.RagSyncClient;
import com.stellar.ragsync.config.RagSyncProperties;
import com.stellar.ragsync.mapper.RagSyncOutboxMapper;
import com.stellar.ragsync.service.RagSyncService;
import com.stellar.result.PageResult;
import com.stellar.service.SpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagSyncServiceImpl implements RagSyncService {

    private static final ObjectMapper MAPPER = new JacksonObjectMapper();

    private final RagSyncOutboxMapper outboxMapper;
    private final RagSyncClient ragSyncClient;
    private final RagSyncProperties properties;
    @Lazy @Autowired
    private SpuService spuService;

    // ===================== 入队 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long enqueueSpuSync(Long spuId, String opType) {
        if (spuId == null) throw new IllegalArgumentException("enqueueSpuSync spuId 为空");
        RagSyncOutbox box = newOutbox("SPU", spuId, opType == null ? "SAVE" : opType);
        // 最小 payload 快照，不额外查 DB（调用方在同一事务内）
        Map<String, Object> snap = new HashMap<>();
        snap.put("spu_id", spuId);
        snap.put("op_type", opType);
        box.setPayloadJson(toJson(snap));
        outboxMapper.insert(box);
        log.info("[RagSyncService] enqueue SPU id={}, opType={}, outboxId={}",
                spuId, opType, box.getId());
        return box.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long enqueueDocSync(String docId, String docType, String title,
                               String contentMd, String opType, int status, String tags) {
        if (docId == null || docId.isEmpty()) throw new IllegalArgumentException("enqueueDocSync docId 为空");
        RagSyncOutbox box = newOutbox(docType == null || docType.isEmpty() ? "DOC" : docType,
                null, opType == null ? "SAVE" : opType);
        // doc 的 doc_id 是字符串，不是 Long，存进 payloadJson
        Map<String, Object> snap = new HashMap<>();
        snap.put("doc_id", docId);
        snap.put("doc_type", box.getBizType());
        snap.put("title", title);
        snap.put("content_md", contentMd != null ? contentMd : "");
        snap.put("status", status);
        if (tags != null && !tags.isEmpty()) snap.put("tags", tags);
        box.setPayloadJson(toJson(snap));
        outboxMapper.insert(box);
        log.info("[RagSyncService] enqueue DOC docId={}, docType={}, opType={}, outboxId={}",
                docId, docType, opType, box.getId());
        return box.getId();
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    private RagSyncOutbox newOutbox(String bizType, Long bizId, String opType) {
        RagSyncOutbox box = new RagSyncOutbox();
        box.setBizType(bizType);
        box.setBizId(bizId);
        box.setOpType(opType);
        box.setSynced(0);
        box.setFailed(0);
        box.setRetryCount(0);
        box.setMaxAttempt(properties.getMaxAttempt() > 0 ? properties.getMaxAttempt() : 3);
        box.setLastErrorMsg("");
        return box;
    }

    // ===================== 处理 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processPendingOne(Long outboxId) {
        if (outboxId == null) return;
        RagSyncOutbox box = outboxMapper.getById(outboxId);
        if (box == null) {
            log.warn("[RagSyncService] 找不到 outbox id={}", outboxId);
            return;
        }
        if (Integer.valueOf(1).equals(box.getSynced()) || Integer.valueOf(1).equals(box.getFailed())) {
            return;
        }
        int maxAtt = box.getMaxAttempt() == null || box.getMaxAttempt() <= 0
                ? 3 : box.getMaxAttempt();
        int retryCnt = box.getRetryCount() == null ? 0 : box.getRetryCount();
        if (retryCnt >= maxAtt) {
            outboxMapper.markFailedOnce(box.getId(), retryCnt, LocalDateTime.now(),
                    box.getLastErrorMsg() == null ? "达到上限未再尝试" : box.getLastErrorMsg(), 1);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int newRetry = retryCnt + 1;
        try {
            String bizType = box.getBizType() == null ? "SPU" : box.getBizType().toUpperCase();
            switch (bizType) {
                case "SPU": {
                    Spu spu = spuService.getById(box.getBizId());
                    if (spu == null) {
                        throw new IllegalStateException("SPU id=" + box.getBizId() + " 不存在，无法同步");
                    }
                    ragSyncClient.syncSpu(spu);
                    break;
                }
                case "DOC":
                case "POLICY":
                case "COUPON":
                case "CATEGORY": {
                    Map<String, Object> payload = buildDocPayload(box);
                    ragSyncClient.syncDoc(payload);
                    break;
                }
                default:
                    throw new IllegalStateException("暂不支持 bizType=" + box.getBizType());
            }
            outboxMapper.markSynced(box.getId(), newRetry, now);
            log.info("[RagSyncService] 同步成功 outboxId={} bizType={} bizId={}",
                    box.getId(), box.getBizType(), box.getBizId());
        } catch (Exception e) {
            log.warn("[RagSyncService] 同步失败 outboxId={} 第{}次：{}",
                    box.getId(), newRetry, e.getMessage());
            int failed = newRetry >= maxAtt ? 1 : 0;
            String err = e.getMessage();
            if (err != null && err.length() > 1000) err = err.substring(0, 1000);
            outboxMapper.markFailedOnce(box.getId(), newRetry, now, err, failed);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildDocPayload(RagSyncOutbox box) {
        Map<String, Object> payload = new HashMap<>();
        try {
            if (box.getPayloadJson() != null && !box.getPayloadJson().isEmpty()) {
                Map<String, Object> snap = MAPPER.readValue(box.getPayloadJson(), new TypeReference<Map<String, Object>>() {});
                if (snap != null) payload.putAll(snap);
            }
        } catch (Exception e) {
            log.warn("[RagSyncService] payloadJson 解析失败 outboxId={}", box.getId());
        }
        if (!payload.containsKey("doc_id")) {
            payload.put("doc_id", String.valueOf(box.getId()));
        }
        if (!payload.containsKey("content_md")) {
            payload.put("content_md", "(empty)");
        }
        return payload;
    }

    @Override
    public int processPendingBatch(int limit) {
        List<RagSyncOutbox> list = outboxMapper.listPending(limit <= 0 ? 100 : limit);
        if (list == null || list.isEmpty()) return 0;
        for (RagSyncOutbox b : list) {
            try {
                processPendingOne(b.getId());
            } catch (Exception e) {
                log.warn("[RagSyncService] 批量处理异常 outboxId={}：{}", b.getId(), e.getMessage());
            }
        }
        return list.size();
    }

    // ================= 管理端：分页查询 / 手动重发 =================

    @Override
    public PageResult listPendingPage(int page, int pageSize) {
        int p = (page <= 0) ? 1 : page;
        int ps = (pageSize <= 0) ? 20 : pageSize;
        long total = outboxMapper.countPending();
        List<RagSyncOutbox> records;
        if (total == 0) {
            records = new java.util.ArrayList<>();
        } else {
            records = outboxMapper.pagePending((p - 1) * ps, ps);
        }
        return new PageResult(total, records == null ? new java.util.ArrayList<>() : records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryOne(Long outboxId) {
        if (outboxId == null) return;
        RagSyncOutbox box = outboxMapper.getById(outboxId);
        if (box == null) return;
        outboxMapper.resetForRetry(outboxId);
        processPendingOne(outboxId);
    }

    @Override
    public PageResult listAllPageFiltered(int page, int pageSize,
                                          Integer status, String eventType, Long bizId) {
        int p = (page <= 0) ? 1 : page;
        int ps = (pageSize <= 0) ? 20 : pageSize;
        long total = outboxMapper.countAllFiltered(status, eventType, bizId);
        List<RagSyncOutbox> records;
        if (total == 0) {
            records = new java.util.ArrayList<>();
        } else {
            records = outboxMapper.pageAllFiltered((p - 1) * ps, ps, status, eventType, bizId);
        }
        return new PageResult(total, records == null ? new java.util.ArrayList<>() : records);
    }

    @Override
    public Map<String, Long> stats() {
        Map<String, Long> res = new HashMap<>();
        res.put("total", outboxMapper.countAll());
        res.put("synced", outboxMapper.countSynced());
        res.put("pending", outboxMapper.countPending());
        res.put("failed", outboxMapper.countFailed());
        return res;
    }
}
