package com.stellar.elasticsearch.service;

import com.stellar.elasticsearch.doc.SpuDocument;
import com.stellar.vo.SearchSuggestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 搜索建议服务 — 提供自动补全 + 拼写纠错。
 * <p>
 * 自动补全（match_phrase_prefix）在用户输入时实时提供候选项；
 * 拼写纠错走本地词典（{@link SearchDictionaryService}：拼音同音字 + 编辑距离），
 * 不依赖 ES 可用性，主搜索无结果时由 {@code SpuSearchService} 调用。
 */
@Slf4j
@Service
public class SearchSuggestService {

    private static final int MAX_SUGGESTIONS = 8;

    private final ElasticsearchOperations esOps;
    private final SynonymEngine synonymEngine;
    private final SearchDictionaryService dictionaryService;

    public SearchSuggestService(ElasticsearchOperations esOps, SynonymEngine synonymEngine,
                                SearchDictionaryService dictionaryService) {
        this.esOps = esOps;
        this.synonymEngine = synonymEngine;
        this.dictionaryService = dictionaryService;
    }

    /**
     * 返回自动补全候选项。
     */
    public SearchSuggestVO suggest(String prefix) {
        if (prefix == null || prefix.trim().length() < 1) return SearchSuggestVO.empty();
        String kw = prefix.trim();

        SearchSuggestVO vo = new SearchSuggestVO();

        // --- 自动补全：match_phrase_prefix ---
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.matchPhrasePrefix(mpp -> mpp.field("name").query(kw)))))
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

        // 无补全候选时才给纠错建议（词典纠错不依赖 ES，ES 挂掉也能提示）
        if (vo.getCompletions().isEmpty()) {
            vo.setCorrection(dictionaryService.correct(kw));
        }
        return vo;
    }

    /** 拼写纠错：委托本地词典（拼音同音字 + 编辑距离），无可靠建议时返回 null。 */
    public String correct(String keyword) {
        return dictionaryService.correct(keyword);
    }
}
