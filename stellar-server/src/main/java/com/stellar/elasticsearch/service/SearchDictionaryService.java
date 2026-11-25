package com.stellar.elasticsearch.service;

import com.stellar.entity.Category;
import com.stellar.mapper.CategoryMapper;
import com.stellar.mapper.SpuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * 搜索纠错词典：把分类名、同义词、商品名及其词元灌进 {@link SearchCorrector}，
 * 定时重建（商品增删改无需逐条同步，最坏延迟一个刷新周期）。
 * <p>
 * 词库优先级：分类名 / 同义词（短、通用，纠错建议首选）> 商品名词元 > 完整商品名。
 * 只收上架商品的名称——纠错建议必须能搜到结果才有意义。
 */
@Slf4j
@Service
public class SearchDictionaryService {

    /** 商品名切分符：空格与常见中英文标点；不切 '.'（避免把版本号拆碎）。 */
    private static final Pattern NAME_SPLIT =
            Pattern.compile("[\\s\\u00A0·•/|,，、;；:：。()（）\\[\\]【】<>《》\\-—_+]+");
    private static final int MIN_TERM_LEN = 2;
    private static final int MAX_TERM_LEN = 24;
    private static final int MAX_NAME_LEN = 40;
    /** 中文核心词多在长词尾部（法式多门冰箱→冰箱、家用空调→空调），补 2–4 字尾缀 */
    private static final int MAX_SUFFIX_LEN = 4;
    private static final long RETRY_COOLDOWN_MS = 60_000;

    private final SpuMapper spuMapper;
    private final CategoryMapper categoryMapper;
    private final SynonymEngine synonymEngine;
    private final SearchCorrector corrector = new SearchCorrector();
    private final ReentrantLock refreshLock = new ReentrantLock();
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private volatile long lastAttemptMs = 0L;

    public SearchDictionaryService(SpuMapper spuMapper, CategoryMapper categoryMapper,
                                   SynonymEngine synonymEngine) {
        this.spuMapper = spuMapper;
        this.categoryMapper = categoryMapper;
        this.synonymEngine = synonymEngine;
    }

    @Scheduled(fixedDelayString = "${stellar.search.dictionary-refresh-ms:600000}",
            initialDelayString = "${stellar.search.dictionary-initial-delay-ms:30000}")
    public void refresh() {
        if (!refreshLock.tryLock()) return;
        lastAttemptMs = System.currentTimeMillis();
        try {
            corrector.refresh(collectTerms());
            loaded.set(true);
            log.debug("Search dictionary refreshed: {} terms", corrector.size());
        } catch (Exception e) {
            // DB 暂不可用等：保留旧词库继续服务，等待下个周期重试
            log.warn("Search dictionary refresh failed: {}", e.getMessage());
        } finally {
            refreshLock.unlock();
        }
    }

    /** 无词典时返回 null（纠错是增强能力，不能影响搜索主流程）。 */
    public String correct(String query) {
        if (!loaded.get() && System.currentTimeMillis() - lastAttemptMs > RETRY_COOLDOWN_MS) {
            refresh();
        }
        return corrector.correct(query);
    }

    private List<SearchCorrector.Term> collectTerms() {
        List<SearchCorrector.Term> terms = new ArrayList<>();
        for (Category category : categoryMapper.list(1, null)) {
            if (category != null) {
                add(terms, category.getName(), 2, MAX_TERM_LEN);
                addChineseSuffixes(terms, category.getName(), 2);
            }
        }
        for (String synonym : synonymEngine.allTerms()) {
            add(terms, synonym, 2, MAX_TERM_LEN);
            addChineseSuffixes(terms, synonym, 2);
        }
        for (String name : spuMapper.listAllNames()) {
            if (name == null) continue;
            add(terms, name, 0, MAX_NAME_LEN);
            for (String token : NAME_SPLIT.split(name)) {
                add(terms, token, 1, MAX_TERM_LEN);
                addChineseSuffixes(terms, token, 1);
            }
        }
        return terms;
    }

    private static void addChineseSuffixes(List<SearchCorrector.Term> terms, String text, int priority) {
        if (text == null) return;
        String trimmed = text.trim();
        int end = trimmed.length();
        int start = end;
        while (start > 0 && isHan(trimmed.charAt(start - 1))) start--;
        int maxLen = Math.min(MAX_SUFFIX_LEN, end - start - 1);
        for (int len = MIN_TERM_LEN; len <= maxLen; len++) {
            terms.add(new SearchCorrector.Term(trimmed.substring(end - len), priority));
        }
    }

    private static boolean isHan(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private static void add(List<SearchCorrector.Term> terms, String text, int priority, int maxLen) {
        if (text == null) return;
        String trimmed = text.trim();
        int len = SearchCorrector.normalize(trimmed).length();
        if (len < MIN_TERM_LEN || len > maxLen) return;
        terms.add(new SearchCorrector.Term(trimmed, priority));
    }
}