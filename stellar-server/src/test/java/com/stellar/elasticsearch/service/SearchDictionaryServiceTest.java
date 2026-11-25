package com.stellar.elasticsearch.service;

import com.stellar.entity.Category;
import com.stellar.mapper.CategoryMapper;
import com.stellar.mapper.SpuMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchDictionaryServiceTest {

    @Test
    @DisplayName("词典：完整名称与中文尾缀都能作为纠错候选")
    void dictionaryContainsFullNamesAndChineseSuffixes() {
        SpuMapper spuMapper = mock(SpuMapper.class);
        CategoryMapper categoryMapper = mock(CategoryMapper.class);
        SynonymEngine synonymEngine = mock(SynonymEngine.class);
        when(categoryMapper.list(1, null))
                .thenReturn(List.of(Category.builder().name("家用空调").build()));
        when(synonymEngine.allTerms()).thenReturn(Set.of("蓝牙耳机"));
        when(spuMapper.listAllNames()).thenReturn(List.of("极净 516L 法式多门冰箱"));

        SearchDictionaryService service = new SearchDictionaryService(
                spuMapper, categoryMapper, synonymEngine);
        service.refresh();

        assertEquals("空调", service.correct("空掉"), "类目尾缀同音错字");
        assertEquals("冰箱", service.correct("冰相"), "商品名尾缀同音错字");
        assertEquals("蓝牙耳机", service.correct("兰牙耳机"), "同义词同音错字");
        assertEquals("极净 516L 法式多门冰箱", service.correct("极静 516L 法式多门冰箱"),
                "完整商品名同音错字");
        assertNull(service.correct("空调"), "本身有效的查询不应纠错");
        assertNull(service.correct("扫地机器人"), "词库外的无关查询不应纠错");
    }

    @Test
    @DisplayName("词典：形近字（不同读音）归一后可作为纠错候选")
    void dictionarySupportsShapeSimilarCorrection() {
        SpuMapper spuMapper = mock(SpuMapper.class);
        CategoryMapper categoryMapper = mock(CategoryMapper.class);
        SynonymEngine synonymEngine = mock(SynonymEngine.class);
        when(categoryMapper.list(1, null))
                .thenReturn(List.of(Category.builder().name("未来").build()));
        when(synonymEngine.allTerms()).thenReturn(Set.of());
        when(spuMapper.listAllNames()).thenReturn(List.of());

        SearchDictionaryService service = new SearchDictionaryService(
                spuMapper, categoryMapper, synonymEngine);
        service.refresh();

        // 未/末 字形接近但读音不同（wei/mo），拼音与编辑距离规则都不覆盖
        assertEquals("未来", service.correct("末来"));
        assertNull(service.correct("未来"), "词库内有效查询不应纠错");
    }
}