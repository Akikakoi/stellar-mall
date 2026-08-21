package com.stellar.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.json.JacksonObjectMapper;
import com.stellar.ragsync.client.RagSyncClient;
import com.stellar.service.ChatBiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 智能查数（ChatBI）服务实现。
 * <p>链路：问题 + 表结构 → RAG 端 LLM 生成 SELECT SQL + 图表配置 →
 * 本地做严格白名单校验（只读）→ JdbcTemplate 执行（限行数/限超时）→
 * 结果回传 RAG 端 LLM 生成自然语言回答。
 * <p>安全设计（纵深防御，LLM 输出一律当作不可信输入）：
 * <ul>
 *   <li>必须单条 SELECT（拒绝分号 / 多语句 / 非 SELECT 开头）</li>
 *   <li>关键字黑名单（DML/DDL/危险函数/系统库，按词边界匹配）</li>
 *   <li>表名白名单：FROM/JOIN 引用的所有表（含子查询内）必须在允许列表</li>
 *   <li>执行层再兜底：专用 JdbcTemplate 限制 maxRows=100、queryTimeout=10s</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBiServiceImpl implements ChatBiService {

    private static final ObjectMapper MAPPER = new JacksonObjectMapper();

    /** 允许查询的表（只读统计口径，不含用户敏感字段表） */
    private static final Set<String> ALLOWED_TABLES = new HashSet<>(Arrays.asList(
            "stellar_mall_order", "stellar_mall_order_item", "stellar_spu", "stellar_sku",
            "stellar_category", "stellar_mall_user", "stellar_after_sale", "stellar_coupon"
    ));

    /** DML/DDL/危险函数黑名单（词边界匹配，大小写不敏感） */
    private static final Pattern FORBIDDEN_KEYWORDS = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|rename|grant|revoke|call|lock|unlock"
                    + "|shutdown|kill|set|use|into|outfile|infile|load_file|sleep|benchmark"
                    + "|information_schema|performance_schema|mysql|sys)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 提取 FROM / JOIN 后的表名（含子查询内的 FROM） */
    private static final Pattern TABLE_REF = Pattern.compile(
            "\\b(from|join)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SELECT_HEAD = Pattern.compile("^\\s*select\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HAS_LIMIT = Pattern.compile("\\blimit\\s+\\d+", Pattern.CASE_INSENSITIVE);

    /** 单次查询最大返回行数（也是发送给 LLM 做总结的最大行数） */
    private static final int MAX_ROWS = 100;
    private static final int QUERY_TIMEOUT_SECONDS = 10;

    /**
     * 提供给 LLM 的表结构说明（与白名单表一一对应，含统计口径提示）。
     */
    private static final String SCHEMA_DDL = """
            stellar_mall_order 订单表: id, order_no(订单号), user_id, total_amount(订单总额), \
            pay_amount(实付金额), status(状态: PENDING待付款/PAID待发货/SHIPPED待收货/COMPLETED已完成/\
            CANCELLED已取消/REFUNDING退款中/REFUNDED已退款), is_refunded(是否已退款: 0否1是), \
            pay_method(支付方式), create_time(下单时间), pay_time(支付时间)
            stellar_mall_order_item 订单明细表: id, order_id, spu_id, sku_id, spu_name(商品名), \
            sku_specs(规格), price(成交单价), qty(数量), subtotal(小计金额)
            stellar_spu 商品表(SPU): id, name(商品名), sub_title(副标题), category_id(一级分类id), \
            category2_id(二级分类id), sale_count(销量), min_price(最低价), max_price(最高价), \
            total_stock(总库存), is_new, is_hot, status(1上架0下架), create_time
            stellar_sku 规格表(SKU): id, spu_id, name(规格名), specs(规格值), price(售价), \
            original_price(原价), stock(库存), warn_stock(库存预警值), status(1启用0停用)
            stellar_category 分类表: id, name(分类名), type(1一级分类 2二级分类), sort, status
            stellar_mall_user 用户表: id, phone(手机号), nickname(昵称), status(1正常0禁用), \
            create_time(注册时间)
            stellar_after_sale 售后表: id, order_id, sku_id, user_id, type(1仅退款/2退货退款/3换货), \
            status(1申请中/2商家审核中/3用户退货中/4退款中/5已完成/6已拒绝/7已取消), create_time
            stellar_coupon 优惠券表: id, name, type, condition_amount(使用门槛), \
            discount_amount(优惠金额), total_count(发放总量), received_count(已领取), \
            used_count(已使用), per_user_limit(每人限领), start_time, end_time, status, create_time
            统计口径: 有效销售额 = stellar_mall_order.status IN ('PAID','SHIPPED','COMPLETED') \
            AND is_refunded = 0 的 SUM(pay_amount)；类目销售额需 stellar_spu 关联 \
            stellar_category（spu.category_id → 一级分类）
            """;

    private final RagSyncClient ragSyncClient;
    private final DataSource dataSource;

    /** 只读受限执行器：限行数 + 限超时，与业务 JdbcTemplate 隔离 */
    private volatile JdbcTemplate chatBiJdbc;

    @Override
    public Map<String, Object> query(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new RuntimeException("问题不能为空");
        }
        question = question.trim();

        // 1. LLM 生成 SQL 计划
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question);
        payload.put("schema_ddl", SCHEMA_DDL);
        Map<String, Object> plan = ragSyncClient.chatBiGenerateSql(payload);

        String sql = validateAndSanitize(String.valueOf(plan.get("sql")));

        // 2. 执行（限行数/超时）
        List<Map<String, Object>> rows;
        try {
            rows = chatBiJdbcTemplate().queryForList(sql);
        } catch (Exception e) {
            log.warn("[ChatBI] SQL 执行失败: sql={}, err={}", sql, e.getMessage());
            throw new RuntimeException("查询执行失败，请换个问法再试（" + e.getMessage() + "）");
        }

        // 3. LLM 总结回答（结果截断，避免 prompt 过长）
        String resultJson = toJson(rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows);
        Map<String, Object> summaryPayload = new LinkedHashMap<>();
        summaryPayload.put("question", question);
        summaryPayload.put("result_json", resultJson);
        String summary;
        try {
            summary = ragSyncClient.chatBiSummary(summaryPayload);
        } catch (Exception e) {
            // 总结失败不致命，降级为提示语，图表数据照常返回
            log.warn("[ChatBI] 结果总结失败，降级: {}", e.getMessage());
            summary = "（AI 总结生成失败，请直接查看下方图表与数据）";
        }

        // 4. 组装返回
        List<String> columns = new ArrayList<>();
        if (!rows.isEmpty()) {
            rows.get(0).keySet().forEach(k -> columns.add(String.valueOf(k)));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("sql", sql);
        result.put("title", String.valueOf(plan.getOrDefault("title", "查询结果")));
        result.put("chartType", String.valueOf(plan.getOrDefault("chart_type", "table")).toLowerCase(Locale.ROOT));
        result.put("xField", String.valueOf(plan.getOrDefault("x_field", "")));
        result.put("yField", String.valueOf(plan.getOrDefault("y_field", "")));
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("summary", summary);
        return result;
    }

    /**
     * SQL 安全校验与规整。LLM 输出不可信，必须过以下关卡：
     * 单条 SELECT / 无分号 / 无黑名单关键字 / 表名全部在白名单 / 自动补 LIMIT。
     */
    String validateAndSanitize(String rawSql) {
        if (rawSql == null || rawSql.trim().isEmpty()) {
            throw new RuntimeException("未生成有效 SQL");
        }
        String sql = rawSql.trim();
        // 去掉结尾分号；只要还含分号（多语句）一律拒绝
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.contains(";")) {
            throw new RuntimeException("AI 生成的 SQL 包含多条语句，已拒绝执行");
        }
        if (!SELECT_HEAD.matcher(sql).find()) {
            throw new RuntimeException("AI 生成的 SQL 不是 SELECT 查询，已拒绝执行");
        }
        Matcher forbidden = FORBIDDEN_KEYWORDS.matcher(sql);
        if (forbidden.find()) {
            throw new RuntimeException("AI 生成的 SQL 包含禁止的关键字「" + forbidden.group() + "」，已拒绝执行");
        }
        Matcher tableRef = TABLE_REF.matcher(sql);
        while (tableRef.find()) {
            String table = tableRef.group(2).toLowerCase(Locale.ROOT);
            if (!ALLOWED_TABLES.contains(table)) {
                throw new RuntimeException("AI 生成的 SQL 引用了未授权的表「" + table + "」，已拒绝执行");
            }
        }
        // 自动补 LIMIT（maxRows 兜底限制行数，这里保证返回语义可控）
        if (!HAS_LIMIT.matcher(sql).find()) {
            sql = sql + " LIMIT " + MAX_ROWS;
        }
        return sql;
    }

    private JdbcTemplate chatBiJdbcTemplate() {
        JdbcTemplate t = chatBiJdbc;
        if (t == null) {
            synchronized (this) {
                t = chatBiJdbc;
                if (t == null) {
                    t = new JdbcTemplate(dataSource);
                    t.setMaxRows(MAX_ROWS);
                    t.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    chatBiJdbc = t;
                }
            }
        }
        return t;
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("查询结果序列化失败", e);
        }
    }
}
