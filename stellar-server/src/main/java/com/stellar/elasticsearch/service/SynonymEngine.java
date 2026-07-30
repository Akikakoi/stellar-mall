package com.stellar.elasticsearch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 同义词引擎 — 从 synonyms.txt 加载同义词组，在查询时扩展搜索词。
 * <p>
 * 文件格式：每行一组同义词，用逗号分隔，如：
 *   手机,电话,移动电话
 *   电脑,计算机,笔记本
 * <p>
 * 查询时：输入"手机"，扩展为 ["手机", "电话", "移动电话"]，原词权重 3x。
 */
@Slf4j
@Service
public class SynonymEngine {

    private static final String SYNONYM_FILE = "elasticsearch/synonyms.txt";

    /** word → 同义词组列表（包含自身） */
    private final Map<String, List<String>> synonymMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource(SYNONYM_FILE);
            if (!resource.exists()) {
                log.info("Synonym file not found at {}, skipping synonym engine", SYNONYM_FILE);
                return;
            }
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("[,，]");
                    List<String> group = new ArrayList<>();
                    for (String p : parts) {
                        String w = p.trim();
                        if (!w.isEmpty()) group.add(w);
                    }
                    if (group.size() < 2) continue;
                    for (String w : group) {
                        synonymMap.put(w, group);
                    }
                }
            }
            log.info("Synonym engine loaded {} synonym groups, {} terms", 
                    synonymMap.values().stream().distinct().count(), synonymMap.size());
        } catch (Exception e) {
            log.warn("Failed to load synonym file: {}", e.getMessage());
        }
    }

    /**
     * 扩展搜索词。返回结果中第一个元素始终是原始词（权重最高）。
     */
    public List<String> expand(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return Collections.emptyList();
        String k = keyword.trim();
        List<String> group = synonymMap.get(k);
        if (group == null) return Collections.singletonList(k);
        // 原词排第一位
        List<String> result = new ArrayList<>();
        result.add(k);
        for (String w : group) {
            if (!w.equals(k)) result.add(w);
        }
        return result;
    }

    /**
     * 检查是否有一个同义词组对应某个词。
     */
    public boolean hasSynonyms(String keyword) {
        if (keyword == null) return false;
        return synonymMap.containsKey(keyword.trim());
    }

    /** 获取所有同义词组（用于补全候选） */
    public Set<String> allTerms() {
        return Collections.unmodifiableSet(synonymMap.keySet());
    }
}
