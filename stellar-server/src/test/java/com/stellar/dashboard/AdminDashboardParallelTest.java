package com.stellar.dashboard;

import com.stellar.controller.admin.AdminDashboardController;
import com.stellar.result.Result;
import com.stellar.service.DailyReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 管理端看板并行查询验证。
 *
 * <p>admin/dashboard 的两个统计接口原先串行执行（enhanced 共 21 次查询：7 项统计 +
 * 最近 7 天各 2 次）。这里用桩 JdbcTemplate 给每条查询加固定耗时，从两个角度验证改造：
 * 返回结构不能变（字段名、条数、日期顺序），以及总耗时确实落在并行量级而非串行量级。</p>
 */
class AdminDashboardParallelTest {

    /** 每条查询模拟的数据库往返耗时。 */
    private static final long QUERY_COST_MS = 50;

    private ExecutorService executor;
    private AdminDashboardController controller;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(8);
        controller = new AdminDashboardController(
                new StubJdbcTemplate(), mock(DailyReportService.class), executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("stats 返回全部 5 项计数")
    void statsReturnsAllCounters() {
        Map<String, Long> data = controller.stats().getData();
        assertNotNull(data);
        assertEquals(5, data.size());
        assertEquals(Long.valueOf(7L), data.get("employeeCount"));
        assertEquals(Long.valueOf(7L), data.get("spuCount"));
        assertEquals(Long.valueOf(7L), data.get("skuCount"));
        assertEquals(Long.valueOf(7L), data.get("orderCount"));
        assertEquals(Long.valueOf(7L), data.get("userCount"));
    }

    @Test
    @DisplayName("enhanced 返回 9 项统计，趋势固定 7 天且升序到今日")
    void enhancedReturnsCompleteStructure() {
        Map<String, Object> data = controller.enhanced().getData();
        assertNotNull(data);
        assertEquals(9, data.size());
        assertEquals(Long.valueOf(5L), data.get("todayOrders"));
        assertEquals(new BigDecimal("12.34"), data.get("todaySales"));
        assertEquals(Long.valueOf(5L), data.get("lowStockCount"));
        assertEquals(Long.valueOf(5L), data.get("pendingOrders"));
        assertEquals(Long.valueOf(5L), data.get("pendingAfterSaleCount"));
        assertTrue(data.get("pendingOrderList") instanceof List);
        assertTrue(data.get("pendingAfterSaleList") instanceof List);

        List<?> orderTrend = (List<?>) data.get("orderTrend");
        List<?> salesTrend = (List<?>) data.get("salesTrend");
        assertEquals(7, orderTrend.size());
        assertEquals(7, salesTrend.size());

        // 末项必须是今天：并行收集后仍要保持原有升序，不能乱序
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE).substring(5);
        assertEquals(today, ((Map<?, ?>) orderTrend.get(6)).get("date"));
        assertEquals(today, ((Map<?, ?>) salesTrend.get(6)).get("date"));
    }

    @Test
    @DisplayName("21 次查询并行执行，总耗时远低于串行")
    void enhancedRunsQueriesInParallel() {
        long start = System.currentTimeMillis();
        controller.enhanced().getData();
        long elapsed = System.currentTimeMillis() - start;

        // 串行下限 = 21 × 50ms = 1050ms；8 线程并行约 150ms。
        // 阈值 600ms：留足调度抖动余量，同时能明确失败于「退化成串行」。
        assertTrue(elapsed < 600,
                "并行编排未生效：enhanced() 耗时 " + elapsed + "ms"
                        + "（21 次查询串行应接近 " + (21 * QUERY_COST_MS) + "ms）");
    }

    /** 桩 JdbcTemplate：不连数据库，按返回类型给固定值，并模拟固定查询耗时。 */
    private static class StubJdbcTemplate extends JdbcTemplate {

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            sleep();
            return requiredType == Long.class ? requiredType.cast(7L) : null;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            sleep();
            if (requiredType == BigDecimal.class) {
                return requiredType.cast(new BigDecimal("12.34"));
            }
            return requiredType == Long.class ? requiredType.cast(5L) : null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            sleep();
            return Collections.emptyList();
        }

        private void sleep() {
            try {
                Thread.sleep(QUERY_COST_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
