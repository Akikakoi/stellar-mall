package com.stellar.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 搜索建议 VO — 供输入框实时补全 + 拼写纠错。
 */
@Data
public class SearchSuggestVO implements Serializable {

    /** 自动补全候选项 */
    private List<String> completions;

    /** 拼写纠错建议（如 "iphoen" → "你是不是要找：iPhone？"） */
    private String correction;

    public static SearchSuggestVO empty() {
        SearchSuggestVO vo = new SearchSuggestVO();
        vo.completions = List.of();
        return vo;
    }
}
