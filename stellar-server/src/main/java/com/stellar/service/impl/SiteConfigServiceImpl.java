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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 站点级配置服务实现。
 * <p>
 * 支持配置键：
 * <ul>
 *   <li>home_bg：商城主页背景图，config_value 存 JSON {"bgImage":"<url>"}；</li>
 *   <li>home_orbit：首屏环绕商品，config_value 存 JSON {"spuIds":[...]}。</li>
 * </ul>
 * 无记录 = 使用默认值。任何单键解析/写入异常都按"默认值"处理，绝不影响页面渲染与主流程。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SiteConfigServiceImpl implements SiteConfigService {

    /** 商城主页背景图配置键 */
    private static final String KEY_HOME_BG = "home_bg";

    /** 首屏环绕商品配置键 */
    private static final String KEY_HOME_ORBIT = "home_orbit";

    /** 环绕商品候选池数量上限（与前端选择器保持一致，前台每次随机展示 6 个） */
    private static final int MAX_HOME_ORBIT = 20;

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

    @Override
    public List<Long> getHomeOrbitSpuIds() {
        SiteConfig cfg = siteConfigMapper.getByKey(KEY_HOME_ORBIT);
        if (cfg == null || cfg.getConfigValue() == null || cfg.getConfigValue().isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(cfg.getConfigValue());
            JsonNode arr = node.path("spuIds");
            if (!arr.isArray()) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>();
            for (JsonNode item : arr) {
                if (item.canConvertToLong() && item.asLong() > 0) {
                    ids.add(item.asLong());
                }
            }
            // 去重并保留候选顺序，限制在候选池数量上限内
            return ids.stream().distinct().limit(MAX_HOME_ORBIT).toList();
        } catch (Exception e) {
            // 存量脏数据按"未配置"处理，前台从商品库随机抽取
            return List.of();
        }
    }

    @Override
    @Transactional
    public void saveHomeOrbitSpuIds(List<Long> spuIds) {
        List<Long> ids = spuIds == null ? List.of() : spuIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(MAX_HOME_ORBIT)
                .toList();
        if (ids.isEmpty()) {
            // 空候选池：删除配置行，前台改为从整个商品库随机抽取
            siteConfigMapper.deleteByKey(KEY_HOME_ORBIT);
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of("spuIds", ids));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化首屏环绕商品配置失败", e);
        }

        SiteConfig exist = siteConfigMapper.getByKey(KEY_HOME_ORBIT);
        if (exist == null) {
            siteConfigMapper.insert(SiteConfig.builder()
                    .configKey(KEY_HOME_ORBIT)
                    .configValue(json)
                    .remark("首屏环绕商品")
                    .updateUser(BaseContext.getCurrentId())
                    .build());
        } else {
            siteConfigMapper.updateValue(KEY_HOME_ORBIT, json, BaseContext.getCurrentId());
        }
    }
}
