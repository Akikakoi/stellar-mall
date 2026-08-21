package com.stellar.service;

import java.util.Map;

/**
 * AI 智能查数（ChatBI）服务：管理员用自然语言提问，
 * LLM 生成安全 SELECT SQL → 本地白名单校验 → 执行 → LLM 总结回答。
 */
public interface ChatBiService {

    /**
     * 处理一次自然语言查数请求。
     *
     * @param question 管理员的问题（如"上周哪个类目卖得最好"）
     * @return {question, sql, title, chartType, xField, yField, columns, rows, summary}
     *         rows 为查询结果（最多 100 行）；失败抛 RuntimeException（含原因）
     */
    Map<String, Object> query(String question);
}
