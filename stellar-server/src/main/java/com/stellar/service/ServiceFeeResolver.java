package com.stellar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.entity.ServiceItem;
import com.stellar.exception.BaseException;
import com.stellar.mapper.SpuServiceMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 保障服务费权威重算（购物车加购与订单提交共用）。
 * <p>解析 serviceInfo JSON 中的服务 code，校验「存在、启用、已挂在该 SPU 下」，
 * 单价合计取服务表权威值，快照用库内数据重建，避免信任前端金额。</p>
 */
@Slf4j
public final class ServiceFeeResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ServiceFeeResolver() {
    }

    /** 重算结果：unitExtra=单件商品的服务费合计（未乘数量）；snapshotJson=库内数据重建的快照。 */
    public record Resolved(BigDecimal unitExtra, String snapshotJson) {
        public static final Resolved EMPTY = new Resolved(BigDecimal.ZERO, null);
    }

    public static Resolved resolve(SpuServiceMapper spuServiceMapper, Long spuId, String serviceInfoJson) {
        if (serviceInfoJson == null || serviceInfoJson.isBlank()) {
            return Resolved.EMPTY;
        }
        List<Map<String, Object>> declared;
        try {
            declared = MAPPER.readValue(serviceInfoJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("[ServiceFeeResolver] serviceInfo 解析失败: {}", serviceInfoJson, e);
            throw new BaseException("保障服务信息有误，请重新选择");
        }
        if (declared == null || declared.isEmpty()) {
            return Resolved.EMPTY;
        }

        List<ServiceItem> allowed = spuId == null
                ? List.of() : spuServiceMapper.listEnabledBySpuId(spuId);
        Map<String, ServiceItem> byCode = allowed.stream()
                .collect(Collectors.toMap(ServiceItem::getCode, s -> s, (a, b) -> a));

        BigDecimal unit = BigDecimal.ZERO;
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Map<String, Object> declaredItem : declared) {
            Object idVal = declaredItem.get("id");
            String code = idVal == null ? "" : String.valueOf(idVal);
            ServiceItem svc = byCode.get(code);
            if (svc == null) {
                String title = declaredItem.get("title") == null
                        ? code : String.valueOf(declaredItem.get("title"));
                throw new BaseException("保障服务「" + title + "」不存在、已下架或该商品未配置，请重新选择");
            }
            if (svc.getPrice() != null) {
                unit = unit.add(svc.getPrice());
            }
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("id", svc.getCode());
            snap.put("title", svc.getTitle());
            snap.put("price", svc.getPrice());
            snapshot.add(snap);
        }
        try {
            String json = snapshot.isEmpty() ? null : MAPPER.writeValueAsString(snapshot);
            return new Resolved(unit, json);
        } catch (Exception e) {
            log.error("[ServiceFeeResolver] 保障服务快照序列化失败", e);
            throw new BaseException("保障服务信息处理失败，请重试");
        }
    }
}
