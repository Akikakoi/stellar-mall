package com.stellar.elasticsearch;

import com.stellar.elasticsearch.service.SearchCorrector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 搜索纠错算法单测：覆盖中文场景最容易出的几类错字，
 * 以及「词库已有词 / 单字 / 无关词不纠错」的负样本。
 */
class SearchCorrectorTest {

    private SearchCorrector corrector;

    @BeforeEach
    void setUp() {
        corrector = new SearchCorrector();
        corrector.refresh(List.of(
                new SearchCorrector.Term("蓝牙耳机", 2),
                new SearchCorrector.Term("蓝牙", 1),
                new SearchCorrector.Term("苹果手机", 2),
                new SearchCorrector.Term("华为", 2),
                new SearchCorrector.Term("红米K70", 1),
                new SearchCorrector.Term("iPhone 15", 1),
                new SearchCorrector.Term("降噪耳机", 1)
        ));
    }

    @Test
    void correctsChineseHomophoneTypos() {
        assertEquals("蓝牙耳机", corrector.correct("兰牙耳机"));
        assertEquals("苹果手机", corrector.correct("平果手机"));
        assertEquals("华为", corrector.correct("华威"));
    }

    @Test
    void correctsMisspellingTranspositionAndAmbiguousChars() {
        assertEquals("红米K70", corrector.correct("红米k7O"));
        assertEquals("iPhone 15", corrector.correct("iphoen 15"));
    }

    @Test
    void correctsShapeSimilarCharactersWithDifferentReadings() {
        SearchCorrector fresh = new SearchCorrector();
        fresh.refresh(List.of(
                new SearchCorrector.Term("未来", 2),
                new SearchCorrector.Term("乌冬面", 1),
                new SearchCorrector.Term("未来科技", 1)
        ));

        // 未/末、乌/鸟 字形接近但拼音不同，只能靠形近字规则召回
        assertEquals("未来", fresh.correct("末来"));
        assertEquals("乌冬面", fresh.correct("鸟冬面"));
        // 多处形近错字（末→未）叠加一处普通错字（枝→技）：形近归一后距离 1
        assertEquals("未来科技", fresh.correct("末来科枝"));
        assertNull(fresh.correct("未日"), "两字查询改一处仍不纠错，避免短词误报");
    }

    @Test
    void shapeGroupsDoNotCollapseUnrelatedWords() {
        SearchCorrector fresh = new SearchCorrector();
        fresh.refresh(List.of(new SearchCorrector.Term("未来", 2), new SearchCorrector.Term("末端", 1)));

        assertNull(fresh.correct("末班车"), "无关词不应被形近表拉近");
        assertNull(fresh.correct("末端"), "词库已有词视为有效查询");
        assertEquals("未来", fresh.correct("末来"));
    }

    @Test
    void doesNotCorrectValidOrShortQueries() {
        assertNull(corrector.correct("蓝牙耳机"), "词库已有词视为有效查询");
        assertNull(corrector.correct("蓝"), "单字改一个就完全变义，不纠错");
        assertNull(corrector.correct(""));
        assertNull(corrector.correct("     "));
    }

    @Test
    void doesNotCorrectUnrelatedQueries() {
        assertNull(corrector.correct("手机"));
        assertNull(corrector.correct("冰箱贴"));
        assertNull(corrector.correct("qwertyuiop"));
    }

    @Test
    void refreshKeepsHighestPriorityForDuplicateTerms() {
        SearchCorrector fresh = new SearchCorrector();
        fresh.refresh(List.of(
                new SearchCorrector.Term("蓝牙耳机", 0),
                new SearchCorrector.Term("蓝牙耳机", 2)
        ));
        assertEquals(1, fresh.size());
        assertEquals("蓝牙耳机", fresh.correct("兰牙耳机"));
    }
}