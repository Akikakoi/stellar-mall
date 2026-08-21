package com.stellar.service.impl;

import com.stellar.ragsync.client.RagSyncClient;
import com.stellar.service.DailyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 经营日报服务实现。
 * <p>数据来源：JdbcTemplate 直查统计 SQL（与仪表盘 enhanced 接口同口径），
 * 汇总后经 RagSyncClient 发给 RAG 端 /api/internal/daily_report，由 qwen-plus 生成日报。
 * <p>各查询单独 try/catch 兜底为 0，避免单表异常导致整份日报生成失败
 * （与 AdminDashboardController 的容错风格一致）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportServiceImpl implements DailyReportService {

    /** 与仪表盘一致的有效销售额状态口径 */
    private static final String PAID_STATUSES = "('PAID', 'SHIPPED', 'COMPLETED')";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final RagSyncClient ragSyncClient;

    @Override
    public Map<String, Object> generate() {
        LocalDate today = LocalDate.now();
        Map<String, Object> payload = buildStats(today);

        String report = ragSyncClient.generateDailyReport(payload);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", today.format(DATE_FMT));
        result.put("report", report);
        result.put("generatedAt", LocalDateTime.now().format(TIME_FMT));
        return result;
    }

    /** 汇总当日经营统计（字段名与 RAG 端 DailyReportRequest 对齐）。 */
    private Map<String, Object> buildStats(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", today.format(DATE_FMT));

        m.put("today_orders", countOrders(today));
        m.put("today_sales", sumSales(today));
        m.put("yesterday_orders", countOrders(yesterday));
        m.put("yesterday_sales", sumSales(yesterday));

        // 待发货（已付款待处理）
        m.put("pending_shipment_orders", queryLongOrDefault(
                "SELECT COUNT(*) FROM stellar_mall_order WHERE status = 'PAID'", 0L));
        // 待处理售后（申请中/审核中/退货中/退款中）
        m.put("pending_after_sales", queryLongOrDefault(
                "SELECT COUNT(*) FROM stellar_after_sale WHERE status IN (1, 2, 3, 4)", 0L));
        // 低库存 SKU
        m.put("low_stock_count", queryLongOrDefault(
                "SELECT COUNT(*) FROM stellar_sku WHERE stock <= warn_stock AND status = 1", 0L));
        // 今日新增用户
        m.put("new_users_today", queryLongOrDefault(
                "SELECT COUNT(*) FROM stellar_mall_user WHERE DATE(create_time) = ?",
                0L, today.format(DATE_FMT)));

        m.put("order_trend", buildTrend(today));
        m.put("top_products", buildTopProducts(today));
        return m;
    }

    /** 近 7 日订单量 + 销售额趋势（含今日）。 */
    private List<Map<String, Object>> buildTrend(LocalDate today) {
        String start = today.minusDays(6).format(DATE_FMT);
        List<Map<String, Object>> trend = new ArrayList<>();
        try {
            List<Map<String, Object>> cntRows = jdbcTemplate.queryForList(
                    "SELECT DATE(create_time) d, COUNT(*) c FROM stellar_mall_order " +
                    "WHERE create_time >= ? GROUP BY DATE(create_time) ORDER BY d",
                    start);
            List<Map<String, Object>> amtRows = jdbcTemplate.queryForList(
                    "SELECT DATE(create_time) d, COALESCE(SUM(pay_amount), 0) s FROM stellar_mall_order " +
                    "WHERE status IN " + PAID_STATUSES + " AND is_refunded = 0 AND create_time >= ? " +
                    "GROUP BY DATE(create_time) ORDER BY d",
                    start);
            Map<String, Long> cntMap = new LinkedHashMap<>();
            for (Map<String, Object> row : cntRows) {
                cntMap.put(String.valueOf(row.get("d")), asLong(row.get("c")));
            }
            Map<String, BigDecimal> amtMap = new LinkedHashMap<>();
            for (Map<String, Object> row : amtRows) {
                amtMap.put(String.valueOf(row.get("d")), asDecimal(row.get("s")));
            }
            for (int i = 6; i >= 0; i--) {
                String full = today.minusDays(i).format(DATE_FMT);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("date", full.substring(5));
                item.put("order_count", cntMap.getOrDefault(full, 0L));
                item.put("sales_amount", amtMap.getOrDefault(full, BigDecimal.ZERO));
                trend.add(item);
            }
        } catch (Exception e) {
            log.warn("[DailyReport] 近7日趋势查询失败，降级为空趋势: {}", e.getMessage());
            for (int i = 6; i >= 0; i--) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("date", today.minusDays(i).format(DATE_FMT).substring(5));
                item.put("order_count", 0L);
                item.put("sales_amount", BigDecimal.ZERO);
                trend.add(item);
            }
        }
        return trend;
    }

    /** 近 7 日热销商品 TOP5（按已付款销售额）。 */
    private List<Map<String, Object>> buildTopProducts(LocalDate today) {
        String start = today.minusDays(6).format(DATE_FMT);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT i.spu_name name, COALESCE(SUM(i.qty), 0) qty, COALESCE(SUM(i.subtotal), 0) sales " +
                    "FROM stellar_mall_order_item i " +
                    "JOIN stellar_mall_order o ON o.id = i.order_id " +
                    "WHERE o.status IN " + PAID_STATUSES + " AND o.is_refunded = 0 " +
                    "AND o.create_time >= ? " +
                    "GROUP BY i.spu_id, i.spu_name ORDER BY sales DESC LIMIT 5",
                    start);
            List<Map<String, Object>> top = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", String.valueOf(row.get("name")));
                item.put("qty", asLong(row.get("qty")));
                item.put("sales", asDecimal(row.get("sales")));
                top.add(item);
            }
            return top;
        } catch (Exception e) {
            log.warn("[DailyReport] 热销商品查询失败，降级为空列表: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private long countOrders(LocalDate date) {
        return queryLongOrDefault(
                "SELECT COUNT(*) FROM stellar_mall_order WHERE DATE(create_time) = ?",
                0L, date.format(DATE_FMT));
    }

    private BigDecimal sumSales(LocalDate date) {
        try {
            BigDecimal v = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(pay_amount), 0) FROM stellar_mall_order " +
                    "WHERE status IN " + PAID_STATUSES + " AND is_refunded = 0 AND DATE(create_time) = ?",
                    BigDecimal.class, date.format(DATE_FMT));
            return v == null ? BigDecimal.ZERO : v;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private long queryLongOrDefault(String sql, long def, Object... args) {
        try {
            Long v = jdbcTemplate.queryForObject(sql, Long.class, args);
            return v == null ? def : v;
        } catch (Exception e) {
            return def;
        }
    }

    private static long asLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static BigDecimal asDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
