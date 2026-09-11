package com.stellar.controller.admin;

import com.stellar.result.Result;
import com.stellar.annotation.RequireRole;
import com.stellar.service.DailyReportService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@Api(tags = "管理端：仪表盘")
public class AdminDashboardController {

    private final JdbcTemplate jdbcTemplate;
    private final DailyReportService dailyReportService;

    /**
     * 净销售额统计 SQL（按订单创建日期）：
     * 已付款未整单退款订单（PAID/SHIPPED/COMPLETED/PARTIAL_REFUNDED，is_refunded=0）
     * 的实付金额，扣除该订单已完成售后（status=5）的退款额，下限 0。
     * 整单退款订单（is_refunded=1）不参与统计（净额本就为 0）。
     */
    private static final String NET_SALES_BY_CREATE_DATE_SQL =
        "SELECT COALESCE(SUM(GREATEST(COALESCE(o.pay_amount, 0) - COALESCE(r.refunded, 0), 0)), 0) " +
        "FROM stellar_mall_order o " +
        "LEFT JOIN (SELECT order_id, SUM(amount) AS refunded FROM stellar_after_sale " +
        "           WHERE status = 5 GROUP BY order_id) r ON r.order_id = o.id " +
        "WHERE o.status IN ('PAID', 'SHIPPED', 'COMPLETED', 'PARTIAL_REFUNDED') " +
        "AND o.is_refunded = 0 AND DATE(o.create_time) = ?";

@RequireRole({1, 2})
    @GetMapping("/stats")
    @ApiOperation("仪表盘统计：员工数 / SPU 数 / SKU 数 / 订单数 / 用户数")
    public Result<Map<String, Long>> stats() {
        Map<String, Long> data = new HashMap<>();
        data.put("employeeCount", count("stellar_employee"));
        data.put("spuCount", count("stellar_spu"));
        data.put("skuCount", count("stellar_sku"));
        data.put("orderCount", count("stellar_mall_order"));
        data.put("userCount", count("stellar_mall_user"));
        return Result.success(data);
    }

@RequireRole({1, 2})
    @GetMapping("/enhanced")
    @ApiOperation("增强统计：今日订单数、今日销售额、低库存数、趋势图")
    public Result<Map<String, Object>> enhanced() {
        Map<String, Object> data = new HashMap<>();
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            Long todayOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stellar_mall_order WHERE DATE(create_time) = ?", Long.class, today);
            data.put("todayOrders", todayOrders == null ? 0L : todayOrders);
        } catch (Exception e) { data.put("todayOrders", 0L); }
        try {
            // 销售额统计（净额口径）：已付款未整单退款订单，部分退款订单扣除已完成售后退款额，
            // 保证部分退款后剩余商品金额仍计入销售额（如 ABCD 四件退 A，BCD 的 2100 仍计入）
            BigDecimal todaySales = jdbcTemplate.queryForObject(
                NET_SALES_BY_CREATE_DATE_SQL,
                BigDecimal.class, today);
            data.put("todaySales", todaySales == null ? BigDecimal.ZERO : todaySales);
        } catch (Exception e) { data.put("todaySales", BigDecimal.ZERO); }
        try {
            Long lowStock = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stellar_sku WHERE stock <= warn_stock AND status = 1", Long.class);
            data.put("lowStockCount", lowStock == null ? 0L : lowStock);
        } catch (Exception e) { data.put("lowStockCount", 0L); }
        try {
            Long pendingOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stellar_mall_order WHERE status = 'PAID'", Long.class);
            data.put("pendingOrders", pendingOrders == null ? 0L : pendingOrders);
        } catch (Exception e) { data.put("pendingOrders", 0L); }
        try {
            // 最近 5 条待发货订单（状态 PAID）
            List<Map<String, Object>> pendingOrderList = jdbcTemplate.queryForList(
                "SELECT o.id, o.order_no AS orderNo, o.pay_amount AS payAmount, " +
                "DATE_FORMAT(o.create_time, '%Y-%m-%d %H:%i') AS createTime, " +
                "COALESCE(u.nickname, CONCAT('用户', o.user_id)) AS userName " +
                "FROM stellar_mall_order o LEFT JOIN stellar_mall_user u ON o.user_id = u.id " +
                "WHERE o.status = 'PAID' ORDER BY o.create_time DESC LIMIT 5");
            data.put("pendingOrderList", pendingOrderList);
        } catch (Exception e) { data.put("pendingOrderList", Collections.emptyList()); }
        try {
            // 待处理售后数：申请中(1) + 商家审核中(2)
            Long pendingAfterSaleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stellar_after_sale WHERE status IN (1, 2)", Long.class);
            data.put("pendingAfterSaleCount", pendingAfterSaleCount == null ? 0L : pendingAfterSaleCount);
        } catch (Exception e) { data.put("pendingAfterSaleCount", 0L); }
        try {
            // 最近 5 条待处理售后
            List<Map<String, Object>> pendingAfterSaleList = jdbcTemplate.queryForList(
                "SELECT a.id, a.type, a.amount, a.status, " +
                "DATE_FORMAT(a.create_time, '%Y-%m-%d %H:%i') AS createTime, " +
                "o.order_no AS orderNo " +
                "FROM stellar_after_sale a LEFT JOIN stellar_mall_order o ON a.order_id = o.id " +
                "WHERE a.status IN (1, 2) ORDER BY a.create_time DESC LIMIT 5");
            data.put("pendingAfterSaleList", pendingAfterSaleList);
        } catch (Exception e) { data.put("pendingAfterSaleList", Collections.emptyList()); }
        // Order trend (last 7 days)
        List<Map<String, Object>> orderTrend = new ArrayList<>();
        List<Map<String, Object>> salesTrend = new ArrayList<>();
        try {
            for (int i = 6; i >= 0; i--) {
                String date = LocalDate.now().minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
                Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM stellar_mall_order " +
                    "WHERE DATE(create_time) = ? AND is_refunded = 0 AND status != 'REFUNDED'", Long.class, date);
                BigDecimal amt = jdbcTemplate.queryForObject(
                    NET_SALES_BY_CREATE_DATE_SQL,
                    BigDecimal.class, date);
                Map<String, Object> item = new HashMap<>();
                item.put("date", date.substring(5));
                item.put("count", cnt == null ? 0L : cnt);
                orderTrend.add(item);
                Map<String, Object> saleItem = new HashMap<>();
                saleItem.put("date", date.substring(5));
                saleItem.put("amount", amt == null ? BigDecimal.ZERO : amt);
                salesTrend.add(saleItem);
            }
        } catch (Exception e) { /* ignore */ }
        data.put("orderTrend", orderTrend);
        data.put("salesTrend", salesTrend);
        return Result.success(data);
    }

@RequireRole({1, 2})
    @GetMapping("/ai-report")
    @ApiOperation("AI 经营日报：汇总当日经营数据，调用 LLM 生成分析报告（耗时较长，前端需放宽超时）")
    public Result<Map<String, Object>> aiReport() {
        try {
            return Result.success(dailyReportService.generate());
        } catch (Exception e) {
            log.error("[AI日报] 生成失败", e);
            return Result.error("生成经营日报失败：" + e.getMessage());
        }
    }

    private long count(String tableName) {
        try {
            Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
            return c == null ? 0L : c;
        } catch (Exception e) { return 0L; }
    }
}
