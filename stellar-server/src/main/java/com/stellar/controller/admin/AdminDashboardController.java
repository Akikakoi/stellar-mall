package com.stellar.controller.admin;

import com.stellar.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@Api(tags = "管理端：仪表盘")
public class AdminDashboardController {

    private final JdbcTemplate jdbcTemplate;

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
            // 销售额统计：已付款且未退款的订单（排除 is_refunded = 1）
            BigDecimal todaySales = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(pay_amount), 0) FROM stellar_mall_order " +
                "WHERE status IN ('PAID', 'SHIPPED', 'COMPLETED') AND is_refunded = 0 AND DATE(create_time) = ?",
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
        // Order trend (last 7 days)
        List<Map<String, Object>> orderTrend = new ArrayList<>();
        List<Map<String, Object>> salesTrend = new ArrayList<>();
        try {
            for (int i = 6; i >= 0; i--) {
                String date = LocalDate.now().minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
                Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM stellar_mall_order WHERE DATE(create_time) = ?", Long.class, date);
                BigDecimal amt = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(pay_amount), 0) FROM stellar_mall_order " +
                    "WHERE status IN ('PAID', 'SHIPPED', 'COMPLETED') AND is_refunded = 0 AND DATE(create_time) = ?",
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

    private long count(String tableName) {
        try {
            Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
            return c == null ? 0L : c;
        } catch (Exception e) { return 0L; }
    }
}