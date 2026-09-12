package com.stellar.controller.admin;

import com.stellar.annotation.RequireRole;
import com.stellar.config.AsyncExecutorConfig;
import com.stellar.result.Result;
import com.stellar.service.DailyReportService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 管理端仪表盘。
 *
 * <p><b>为什么这里适合并行查询</b>：本控制器直接持有 {@link JdbcTemplate}，两个统计接口都
 * <b>不在事务里</b>（没有 {@code @Transactional}），每次查询互相独立、彼此不依赖；
 * 而看板又天然是「一次请求里跑十几二十条统计」。串行执行时总耗时是各条查询之和，
 * 并行后降到约「查询数 / 并发度」——这也是唯一能安全使用并行查询的场景。</p>
 *
 * <p><b>反面：不要在 {@code @Transactional} 方法内部这么做</b>。Spring 事务上下文绑定在
 * ThreadLocal 上，子线程不会加入主线程事务，会各自从连接池另取连接、读不到主线程
 * 尚未提交的数据，还会成倍放大连接占用。</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/dashboard")
@Api(tags = "管理端：仪表盘")
public class AdminDashboardController {

    private final JdbcTemplate jdbcTemplate;
    private final DailyReportService dailyReportService;
    private final Executor aggregateExecutor;

    public AdminDashboardController(JdbcTemplate jdbcTemplate,
                                    DailyReportService dailyReportService,
                                    @Qualifier(AsyncExecutorConfig.AGGREGATE_EXECUTOR) Executor aggregateExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.dailyReportService = dailyReportService;
        this.aggregateExecutor = aggregateExecutor;
    }

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
        // 5 次 COUNT(*) 相互独立，并行执行；count() 内部已消化异常，join 不会抛
        CompletableFuture<Long> employeeCount = async(() -> count("stellar_employee"));
        CompletableFuture<Long> spuCount = async(() -> count("stellar_spu"));
        CompletableFuture<Long> skuCount = async(() -> count("stellar_sku"));
        CompletableFuture<Long> orderCount = async(() -> count("stellar_mall_order"));
        CompletableFuture<Long> userCount = async(() -> count("stellar_mall_user"));

        Map<String, Long> data = new HashMap<>();
        data.put("employeeCount", employeeCount.join());
        data.put("spuCount", spuCount.join());
        data.put("skuCount", skuCount.join());
        data.put("orderCount", orderCount.join());
        data.put("userCount", userCount.join());
        return Result.success(data);
    }

    @RequireRole({1, 2})
    @GetMapping("/enhanced")
    @ApiOperation("增强统计：今日订单数、今日销售额、低库存数、趋势图")
    public Result<Map<String, Object>> enhanced() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 本接口共 21 次查询：7 项统计 + 最近 7 天各 2 次（订单数 / 净销售额）。
        // 原先全部串行，改为并行编排后总耗时从「21 次之和」降到约「并发度分之一」。
        // 每个任务内部保留原有的 try-catch 兜底，单条查询失败只让它自己返回默认值，
        // 不会影响其余统计（此前趋势循环是整体包一个 try，一条失败整天趋势都空）。
        CompletableFuture<Long> todayOrders = async(() -> queryLong(
                "SELECT COUNT(*) FROM stellar_mall_order WHERE DATE(create_time) = ?", today));
        CompletableFuture<BigDecimal> todaySales = async(() -> queryDecimal(
                NET_SALES_BY_CREATE_DATE_SQL, today));
        CompletableFuture<Long> lowStockCount = async(() -> queryLong(
                "SELECT COUNT(*) FROM stellar_sku WHERE stock <= warn_stock AND status = 1"));
        CompletableFuture<Long> pendingOrders = async(() -> queryLong(
                "SELECT COUNT(*) FROM stellar_mall_order WHERE status = 'PAID'"));
        CompletableFuture<List<Map<String, Object>>> pendingOrderList = async(() -> queryList(
                "SELECT o.id, o.order_no AS orderNo, o.pay_amount AS payAmount, " +
                "DATE_FORMAT(o.create_time, '%Y-%m-%d %H:%i') AS createTime, " +
                "COALESCE(u.nickname, CONCAT('用户', o.user_id)) AS userName " +
                "FROM stellar_mall_order o LEFT JOIN stellar_mall_user u ON o.user_id = u.id " +
                "WHERE o.status = 'PAID' ORDER BY o.create_time DESC LIMIT 5"));
        CompletableFuture<Long> pendingAfterSaleCount = async(() -> queryLong(
                "SELECT COUNT(*) FROM stellar_after_sale WHERE status IN (1, 2)"));
        CompletableFuture<List<Map<String, Object>>> pendingAfterSaleList = async(() -> queryList(
                "SELECT a.id, a.type, a.amount, a.status, " +
                "DATE_FORMAT(a.create_time, '%Y-%m-%d %H:%i') AS createTime, " +
                "o.order_no AS orderNo " +
                "FROM stellar_after_sale a LEFT JOIN stellar_mall_order o ON a.order_id = o.id " +
                "WHERE a.status IN (1, 2) ORDER BY a.create_time DESC LIMIT 5"));

        // 最近 7 天趋势：每天 2 条查询，共 14 次，全部并行
        List<LocalDate> days = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            days.add(LocalDate.now().minusDays(i));
        }
        List<CompletableFuture<Map<String, Object>>> orderTrendFutures = days.stream()
                .map(day -> async(() -> orderTrendItem(day)))
                .collect(Collectors.toList());
        List<CompletableFuture<Map<String, Object>>> salesTrendFutures = days.stream()
                .map(day -> async(() -> salesTrendItem(day)))
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("todayOrders", todayOrders.join());
        data.put("todaySales", todaySales.join());
        data.put("lowStockCount", lowStockCount.join());
        data.put("pendingOrders", pendingOrders.join());
        data.put("pendingOrderList", pendingOrderList.join());
        data.put("pendingAfterSaleCount", pendingAfterSaleCount.join());
        data.put("pendingAfterSaleList", pendingAfterSaleList.join());
        data.put("orderTrend", orderTrendFutures.stream()
                .map(CompletableFuture::join).collect(Collectors.toList()));
        data.put("salesTrend", salesTrendFutures.stream()
                .map(CompletableFuture::join).collect(Collectors.toList()));
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

    // ======================== 并行编排与查询兜底 ========================

    /** 把一条独立查询提交到聚合线程池执行。 */
    private <T> CompletableFuture<T> async(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, aggregateExecutor);
    }

    /** 单值计数查询：失败或为空一律返回 0，保证 join 不会抛异常。 */
    private Long queryLong(String sql, Object... args) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
            return value == null ? 0L : value;
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 金额查询：失败或为空返回 0，口径与之前的 BigDecimal.ZERO 兜底一致。 */
    private BigDecimal queryDecimal(String sql, Object... args) {
        try {
            BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
            return value == null ? BigDecimal.ZERO : value;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /** 列表查询：失败返回空列表。 */
    private List<Map<String, Object>> queryList(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForList(sql, args);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 某天的订单数（趋势图用）。 */
    private Map<String, Object> orderTrendItem(LocalDate day) {
        String date = day.format(DateTimeFormatter.ISO_LOCAL_DATE);
        Long count = queryLong("SELECT COUNT(*) FROM stellar_mall_order " +
                "WHERE DATE(create_time) = ? AND is_refunded = 0 AND status != 'REFUNDED'", date);
        Map<String, Object> item = new HashMap<>();
        item.put("date", date.substring(5));
        item.put("count", count);
        return item;
    }

    /** 某天的净销售额（趋势图用）。 */
    private Map<String, Object> salesTrendItem(LocalDate day) {
        String date = day.format(DateTimeFormatter.ISO_LOCAL_DATE);
        BigDecimal amount = queryDecimal(NET_SALES_BY_CREATE_DATE_SQL, date);
        Map<String, Object> item = new HashMap<>();
        item.put("date", date.substring(5));
        item.put("amount", amount);
        return item;
    }

    private long count(String tableName) {
        try {
            Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
            return c == null ? 0L : c;
        } catch (Exception e) {
            return 0L;
        }
    }
}
