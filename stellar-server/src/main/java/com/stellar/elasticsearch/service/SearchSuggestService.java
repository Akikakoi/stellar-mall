package com.stellar.elasticsearch.service;

import com.stellar.elasticsearch.doc.SpuDocument;
import com.stellar.vo.SearchSuggestVO;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 搜索建议服务 — 提供自动补全 + 拼写纠错。
 * <p>
 * 自动补全（match_phrase_prefix）在用户输入时实时提供候选项；
 * 拼写纠错（fuzziness 回查）当主搜索无结果时给出纠错建议。
 */
@Slf4j
@Service
public class SearchSuggestService {

    private static final int MAX_SUGGESTIONS = 8;

    private final ElasticsearchOperations esOps;
    private final SynonymEngine synonymEngine;

    public SearchSuggestService(ElasticsearchOperations esOps, SynonymEngine synonymEngine) {
        this.esOps = esOps;
        this.synonymEngine = synonymEngine;
    }

    /**
     * 返回自动补全候选项。
     */
    public SearchSuggestVO suggest(String prefix) {
        if (prefix == null || prefix.trim().length() < 1) return SearchSuggestVO.empty();
        String kw = prefix.trim();

        SearchSuggestVO vo = new SearchSuggestVO();

        // --- 自动补全：match_phrase_prefix ---
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.matchPhrasePrefixQuery("name", kw));

        NativeSearchQuery query = new NativeSearchQueryBuilder()
                .withQuery(boolQuery)
                .withPageable(PageRequest.of(0, MAX_SUGGESTIONS))
                .withFields("name")
                .build();

        try {
            SearchHits<SpuDocument> hits = esOps.search(query, SpuDocument.class);
            Set<String> completions = new LinkedHashSet<>();
            for (SearchHit<SpuDocument> hit : hits) {
                String name = hit.getContent().getName();
                if (name != null) completions.add(name);
            }

            // 加上同义词候选项
            for (String term : synonymEngine.allTerms()) {
                if (term.startsWith(kw) && !term.equals(kw)) {
                    completions.add(term);
                }
            }

            vo.setCompletions(new ArrayList<>(completions).subList(0, Math.min(completions.size(), MAX_SUGGESTIONS)));
        } catch (Exception e) {
            log.warn("Autocomplete failed: {}", e.getMessage());
            vo.setCompletions(List.of());
        }

        return vo;
    }

    /**
     * 拼写纠错：当搜索无结果时，用 fuzziness 模糊匹配给出纠错建议。
     */
    public String correct(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return null;
        String kw = keyword.trim();
        if (kw.length() < 3) return null;

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.multiMatchQuery(kw, "name", "subTitle")
                        .fuzziness("AUTO"));

        NativeSearchQuery query = new NativeSearchQueryBuilder()
                .withQuery(boolQuery)
                .withPageable(PageRequest.of(0, 3))
                .withFields("name")
                .build();

        try {
            SearchHits<SpuDocument> hits = esOps.search(query, SpuDocument.class);
            if (hits.getTotalHits() > 0) {
                // 拿第一个高得分的商品名作为纠错建议
                return hits.getSearchHit(0).getContent().getName();
            }
        } catch (Exception e) {
            log.warn("Spell correction failed: {}", e.getMessage());
        }
        return null;
    }
}
