package com.stellar.ragsync.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.json.JacksonObjectMapper;
import com.stellar.entity.Sku;
import com.stellar.entity.Spu;
import com.stellar.ragsync.client.RagSyncClient;
import com.stellar.ragsync.config.RagSyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认 RagSyncClient：发 HTTP POST 到 RAG 端 /api/internal/sync_spu，
 * Header X-Stellar-Rag-Sync-Secret 携带共享密钥。
 * <p>
 * 测试里使用 @MockBean RagSyncClient，会把这个实现替换成 mockito mock。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultRagSyncClient implements RagSyncClient {

    private static final ObjectMapper MAPPER = new JacksonObjectMapper();

    private static final String SYNC_SPU_PATH = "/api/internal/sync_spu";
    private static final String SYNC_DOC_PATH = "/api/internal/sync_doc";
    private static final String DAILY_REPORT_PATH = "/api/internal/daily_report";
    private static final String CHAT_BI_SQL_PATH = "/api/internal/chat_bi/sql";
    private static final String CHAT_BI_SUMMARY_PATH = "/api/internal/chat_bi/summary";
    private static final String HEADER_SECRET = "X-Stellar-Rag-Sync-Secret";

    private final RagSyncProperties properties;

    private CloseableHttpClient httpClient(int timeoutMs) {
        int timeout = timeoutMs > 0 ? timeoutMs : 10_000;
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .build();
        return HttpClientBuilder.create().setDefaultRequestConfig(config).build();
    }

    @Override
    public boolean syncSpu(Spu spu) {
        if (spu == null || spu.getId() == null) {
            throw new IllegalArgumentException("syncSpu 参数非法：spu 为空或缺少 id");
        }
        Map<String, Object> body = buildSpuPayload(spu);
        return doPost(SYNC_SPU_PATH, body, "SPU", spu.getId());
    }

    @Override
    public boolean syncDoc(Map<String, Object> docPayload) {
        return doPost(SYNC_DOC_PATH, docPayload, "DOC",
                docPayload.getOrDefault("doc_id", "?").toString());
    }

    @Override
    public String generateDailyReport(Map<String, Object> statsPayload) {
        int timeout = properties.getReportTimeoutMs() > 0 ? properties.getReportTimeoutMs() : 90_000;
        Map<String, Object> parsed = postJson(DAILY_REPORT_PATH, statsPayload, timeout);

        // RAG 端统一响应包装：{code: 0, message, data: {report, model}}
        Object code = parsed == null ? null : parsed.get("code");
        if (code == null || !"0".equals(String.valueOf(code))) {
            throw new RuntimeException("RAG daily_report 返回业务失败："
                    + (parsed == null ? "空响应" : String.valueOf(parsed.get("message"))));
        }
        Object dataObj = parsed.get("data");
        Object report = dataObj instanceof Map ? ((Map<?, ?>) dataObj).get("report") : null;
        if (report == null || String.valueOf(report).trim().isEmpty()) {
            throw new RuntimeException("RAG daily_report 未返回日报内容");
        }
        log.info("[RagSyncClient] AI 经营日报生成成功，长度={}", String.valueOf(report).length());
        return String.valueOf(report);
    }

    @Override
    public Map<String, Object> chatBiGenerateSql(Map<String, Object> payload) {
        return postForData(CHAT_BI_SQL_PATH, payload);
    }

    @Override
    public String chatBiSummary(Map<String, Object> payload) {
        Map<String, Object> data = postForData(CHAT_BI_SUMMARY_PATH, payload);
        Object summary = data.get("summary");
        if (summary == null || String.valueOf(summary).trim().isEmpty()) {
            throw new RuntimeException("RAG chat_bi/summary 未返回总结内容");
        }
        return String.valueOf(summary);
    }

    /**
     * 调 RAG 内部接口并返回统一包装里的 data（必须是 JSON 对象）。
     * LLM 类接口超时统一走 reportTimeoutMs。
     */
    private Map<String, Object> postForData(String path, Map<String, Object> payload) {
        int timeout = properties.getReportTimeoutMs() > 0 ? properties.getReportTimeoutMs() : 90_000;
        Map<String, Object> parsed = postJson(path, payload, timeout);
        Object code = parsed == null ? null : parsed.get("code");
        if (code == null || !"0".equals(String.valueOf(code))) {
            throw new RuntimeException("RAG " + path + " 返回业务失败："
                    + (parsed == null ? "空响应" : String.valueOf(parsed.get("message"))));
        }
        Object data = parsed.get("data");
        if (!(data instanceof Map)) {
            throw new RuntimeException("RAG " + path + " 响应 data 不是 JSON 对象：" + data);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) data;
        return dataMap;
    }

    private boolean doPost(String path, Map<String, Object> body, String bizType, Object bizId) {
        Map<String, Object> parsed = postJson(path, body, properties.getTimeoutMs());
        Object ok = parsed == null ? null : parsed.get("ok");
        if (!Boolean.TRUE.equals(ok) && !"true".equals(String.valueOf(ok))) {
            throw new RuntimeException("RAG " + path + " ok=false，响应：" + parsed);
        }
        log.info("[RagSyncClient] {} id={} 同步成功", bizType, bizId);
        return true;
    }

    /**
     * 通用 POST：发送 JSON + 共享密钥，返回解析后的响应 Map。
     * 非 2xx / 网络异常 / JSON 解析失败统一抛 RuntimeException。
     */
    private Map<String, Object> postJson(String path, Map<String, Object> body, int timeoutMs) {
        String url = trimSlash(properties.getBaseUrl()) + path;
        String json = toJson(body);

        try (CloseableHttpClient client = httpClient(timeoutMs)) {
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json; charset=utf-8");
            post.setHeader(HEADER_SECRET, properties.getInternalSyncSecret());
            post.setEntity(new StringEntity(json, StandardCharsets.UTF_8));

            try (CloseableHttpResponse resp = client.execute(post)) {
                int code = resp.getStatusLine().getStatusCode();
                HttpEntity entity = resp.getEntity();
                String respText = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
                if (code < 200 || code >= 300) {
                    throw new RuntimeException("RAG " + path + " 返回非 2xx 状态码：" + code + "，body=" + respText);
                }
                return MAPPER.readValue(respText, new TypeReference<Map<String, Object>>() {});
            }
        } catch (IOException e) {
            throw new RuntimeException("RAG " + path + " HTTP 调用失败：" + e.getMessage(), e);
        }
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    // ============ 内部：SPU → RAG SyncSpuRequest payload ============
    private Map<String, Object> buildSpuPayload(Spu spu) {
        Map<String, Object> m = new HashMap<>();
        m.put("spu_id", spu.getId());
        m.put("name", nvl(spu.getName()));
        m.put("subtitle", nvl(spu.getSubTitle()));

        // category_path: 分类名称
        String catPath = buildCategoryPath(spu);
        if (catPath != null) m.put("category_path", catPath);

        m.put("status", spu.getStatus() == null ? 0 : spu.getStatus());
        m.put("min_price", spu.getMinPrice());
        m.put("main_image", nvl(spu.getMainImage()));

        // description_md 不能为空字符串（Python pydantic min_length=1），兜底文本
        String descMd = nvl(spu.getDescriptionMd());
        m.put("description_md", descMd.isEmpty() ? "（暂无商品详情描述）" : descMd);

        // spec_table_markdown: 从 SKU 列表构建规格参数表格
        String specTable = buildSpecTable(spu);
        if (specTable != null) m.put("spec_table_markdown", specTable);

        // sku_count
        List<Sku> skus = spu.getSkuList();
        if (skus != null && !skus.isEmpty()) {
            m.put("sku_count", skus.size());
        } else {
            m.put("sku_count", spu.getSkuCount() == null ? 0 : spu.getSkuCount());
        }

        // tags
        m.put("tags", buildTags(spu));
        return m;
    }

    private String buildCategoryPath(Spu spu) {
        String c1 = nvl(spu.getCategoryName());
        return c1.isEmpty() ? null : c1;
    }

    /**
     * 从 SKU 列表构建 Markdown 规格参数表。
     * 格式：
     * | 规格 | 价格 | 库存 |
     * | --- | --- | --- |
     * | 颜色:红, 尺寸:XL | ¥99.00 | 50 |
     */
    private String buildSpecTable(Spu spu) {
        List<Sku> skus = spu.getSkuList();
        if (skus == null || skus.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("| 规格 | 价格 | 库存 |\n");
        sb.append("| --- | --- | --- |\n");
        for (Sku sku : skus) {
            String specs = sku.getSpecs() != null ? sku.getSpecs() : "";
            String name = nvl(sku.getName());
            String specLabel = name.isEmpty() ? specs
                    : (specs.isEmpty() ? name : name + " / " + specs);
            String price = sku.getPrice() != null ? "¥" + sku.getPrice().toString() : "-";
            String stock = sku.getStock() != null ? sku.getStock().toString() : "0";
            sb.append("| ").append(escapePipe(specLabel))
              .append(" | ").append(price)
              .append(" | ").append(stock).append(" |\n");
        }
        return sb.toString();
    }

    private static String escapePipe(String s) {
        return s.replace("|", "\\|");
    }

    private List<String> buildTags(Spu spu) {
        List<String> t = new java.util.ArrayList<>();
        t.add("mall_spu");
        if (spu.getCategoryId() != null) t.add("cat:" + spu.getCategoryId());
        if (spu.getCategory2Id() != null) t.add("cat2:" + spu.getCategory2Id());
        if (Integer.valueOf(1).equals(spu.getIsNew())) t.add("is_new");
        if (Integer.valueOf(1).equals(spu.getIsHot())) t.add("is_hot");
        if (Integer.valueOf(1).equals(spu.getStatus())) t.add("on_shelf");
        return t;
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        String s = url.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String nvl(String s) {
        return StringUtils.hasText(s) ? s : "";
    }
}
