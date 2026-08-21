package com.stellar.service;

import java.util.Map;

/**
 * AI 经营日报服务：汇总当日经营统计数据，调用 RAG 端 LLM 生成自然语言日报。
 */
public interface DailyReportService {

    /**
     * 生成今日经营日报。
     * <p>统计口径与 AdminDashboardController#enhanced 保持一致：
     * 销售额 = 已付款未退款订单（PAID/SHIPPED/COMPLETED，is_refunded=0）。
     *
     * @return {date, report, generatedAt}，report 为 LLM 生成的日报文本
     * @throws RuntimeException 统计或 RAG/LLM 调用失败时抛出（含原因）
     */
    Map<String, Object> generate();
}
