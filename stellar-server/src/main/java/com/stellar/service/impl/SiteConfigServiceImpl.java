package com.stellar.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.context.BaseContext;
import com.stellar.entity.SiteConfig;
import com.stellar.mapper.SiteConfigMapper;
import com.stellar.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 站点级配置服务实现。
 * <p>
 * 当前支持配置键 home_bg（商城主页背景图）：
 * config_value 存 JSON {"bgImage":"<url>"}，无记录 = 使用默认背景图。
 * 任何单键解析/写入异常都按"默认值"处理，绝不影响页面渲染与主流程。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SiteConfigServiceImpl implements SiteConfigService {

    /** 商城主页背景图配置键 */
    private static final String KEY_HOME_BG = "home_bg";

    private final SiteConfigMapper siteConfigMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String getBgImage() {
        SiteConfig cfg = siteConfigMapper.getByKey(KEY_HOME_BG);
        if (cfg == null || cfg.getConfigValue() == null || cfg.getConfigValue().isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(cfg.getConfigValue());
            return node.path("bgImage").asText("");
        } catch (Exception e) {
            // 存量脏数据按默认处理，不阻断
            return "";
        }
    }

    @Override
    @Transactional
    public void saveBgImage(String bgImage) {
        if (bgImage == null || bgImage.isBlank()) {
            // 恢复默认：删除配置行，前端 CSS fallback 即默认图
            siteConfigMapper.deleteByKey(KEY_HOME_BG);
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of("bgImage", bgImage.trim()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化背景配置失败", e);
        }

        SiteConfig exist = siteConfigMapper.getByKey(KEY_HOME_BG);
        if (exist == null) {
            siteConfigMapper.insert(SiteConfig.builder()
                    .configKey(KEY_HOME_BG)
                    .configValue(json)
                    .remark("商城主页背景图")
                    .updateUser(BaseContext.getCurrentId())
                    .build());
        } else {
            siteConfigMapper.updateValue(KEY_HOME_BG, json, BaseContext.getCurrentId());
        }
    }
}
