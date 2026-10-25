package com.stellar.product;

import com.stellar.entity.Spu;
import com.stellar.mapper.SkuMapper;
import com.stellar.mapper.SpuMapper;
import com.stellar.ragsync.service.RagSyncService;
import com.stellar.service.SiteConfigService;
import com.stellar.service.impl.SpuServiceImpl;
import com.stellar.vo.OrbitSpuVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 首页首屏环绕商品接口（/user/spu/orbit）的服务层单测。
 * <p>
 * 只验证「候选池优先 + 失效过滤 + 随机兜底 + 近期去重 + 轻量字段」这几个对外可感知的契约，
 * 不启动 Spring 上下文。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SpuOrbitServiceTest {

    @Mock
    private SpuMapper spuMapper;
    @Mock
    private SkuMapper skuMapper;
    @Mock
    private RagSyncService ragSyncService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SiteConfigService siteConfigService;

    @InjectMocks
    private SpuServiceImpl spuService;

    private Spu spu(long id, String name, int status, String mainImage) {
        Spu spu = new Spu();
        spu.setId(id);
        spu.setName(name);
        spu.setStatus(status);
        spu.setMainImage(mainImage);
        return spu;
    }

    @Test
    void listOrbit_usesConfiguredPool_whenCandidatesValid() {
        when(siteConfigService.getHomeOrbitSpuIds()).thenReturn(List.of(11L, 12L));
        when(spuMapper.listByIds(anyList())).thenReturn(List.of(
                spu(11L, "商品A", 1, "https://oss/a.png"),
                spu(12L, "商品B", 1, "https://oss/b.png")
        ));

        List<OrbitSpuVO> result = spuService.listOrbit(8);

        assertEquals(2, result.size());
        // 顺序随机，这里按 id 集合断言
        assertEquals(List.of(11L, 12L), result.stream().map(OrbitSpuVO::getId).sorted().toList());
        assertEquals("商品A", result.stream().filter(v -> v.getId() == 11L).findFirst().orElseThrow().getName());
        assertEquals("https://oss/b.png", result.stream().filter(v -> v.getId() == 12L).findFirst().orElseThrow().getMainImage());
        verify(spuMapper, never()).listRandom(anyInt());
    }

    @Test
    void listOrbit_fallsBackToRandom_whenNoConfiguredPool() {
        when(siteConfigService.getHomeOrbitSpuIds()).thenReturn(List.of());
        when(spuMapper.listRandom(6)).thenReturn(List.of(
                spu(21L, "商品C", 1, "https://oss/c.png"),
                spu(22L, "商品D", 1, "https://oss/d.png"),
                spu(23L, "商品E", 1, "https://oss/e.png")
        ));

        List<OrbitSpuVO> result = spuService.listOrbit(6);

        assertEquals(List.of(21L, 22L, 23L), result.stream().map(OrbitSpuVO::getId).toList());
        verify(spuMapper, never()).listByIds(anyList());
    }

    @Test
    void listOrbit_ignoresInvalidCandidates_andRandomFallsBack() {
        when(siteConfigService.getHomeOrbitSpuIds()).thenReturn(List.of(31L, 32L));
        when(spuMapper.listByIds(anyList())).thenReturn(List.of(
                spu(31L, "已下架", 0, "https://oss/off.png"),
                spu(32L, "无主图", 1, " ")
        ));
        when(spuMapper.listRandom(8)).thenReturn(List.of(spu(33L, "随机商品", 1, "https://oss/rand.png")));

        List<OrbitSpuVO> result = spuService.listOrbit(8);

        assertEquals(List.of(33L), result.stream().map(OrbitSpuVO::getId).toList());
    }

    @Test
    void listOrbit_limitsCountToSafeRange() {
        when(siteConfigService.getHomeOrbitSpuIds()).thenReturn(List.of());
        when(spuMapper.listRandom(20)).thenReturn(List.of(
                spu(41L, "A", 1, "https://oss/41.png"),
                spu(42L, "B", 1, "https://oss/42.png")
        ));

        List<OrbitSpuVO> result = spuService.listOrbit(999);

        assertEquals(2, result.size());
        verify(spuMapper).listRandom(20);
    }

    @Test
    void listOrbit_excludesRecentIds_forRandomPool() {
        when(siteConfigService.getHomeOrbitSpuIds()).thenReturn(List.of());
        when(spuMapper.listRandomExcluding(eq(8), argThat((List<Long> ids) ->
                ids.size() == 2 && ids.containsAll(List.of(1L, 2L))))).thenReturn(List.of(
                spu(51L, "E1", 1, "https://oss/51.png"),
                spu(52L, "E2", 1, "https://oss/52.png"),
                spu(53L, "E3", 1, "https://oss/53.png"),
                spu(54L, "E4", 1, "https://oss/54.png"),
                spu(55L, "E5", 1, "https://oss/55.png"),
                spu(56L, "E6", 1, "https://oss/56.png"),
                spu(57L, "E7", 1, "https://oss/57.png"),
                spu(58L, "E8", 1, "https://oss/58.png")
        ));

        // 传入重复/非法 id，服务层应清洗成 [1,2]
        List<OrbitSpuVO> result = spuService.listOrbit(8, List.of(1L, 1L, 0L, -3L, 2L));

        assertEquals(8, result.size());
        verify(spuMapper).listRandomExcluding(eq(8), anyList());
        verify(spuMapper, never()).listRandom(anyInt());
    }

    @Test
    void listOrbit_topsUpWithUnseenProducts_whenExcludedPoolTooSmall() {
        when(siteConfigService.getHomeOrbitSpuIds()).thenReturn(List.of());
        when(spuMapper.listRandomExcluding(eq(8), anyList())).thenReturn(List.of(
                spu(61L, "A", 1, "https://oss/61.png"),
                spu(62L, "B", 1, "https://oss/62.png"),
                spu(63L, "C", 1, "https://oss/63.png")
        ));
        // 补足批次里故意包含已选商品 61，验证 id 去重
        when(spuMapper.listRandom(anyInt())).thenReturn(List.of(
                spu(61L, "A", 1, "https://oss/61.png"),
                spu(64L, "D", 1, "https://oss/64.png"),
                spu(65L, "E", 1, "https://oss/65.png"),
                spu(66L, "F", 1, "https://oss/66.png"),
                spu(67L, "G", 1, "https://oss/67.png"),
                spu(68L, "H", 1, "https://oss/68.png"),
                spu(69L, "I", 1, "https://oss/69.png")
        ));

        List<OrbitSpuVO> result = spuService.listOrbit(8, List.of(1L, 2L));
        List<Long> ids = result.stream().map(OrbitSpuVO::getId).toList();

        assertEquals(8, ids.size());
        assertEquals(8, new HashSet<>(ids).size(), "补足后不应出现重复商品");
        assertEquals(List.of(61L, 62L, 63L), ids.subList(0, 3), "先展示排除后的新商品");
    }

    @Test
    void listOrbit_configuredPool_putsUnseenBeforeRecentlyShown() {
        when(siteConfigService.getHomeOrbitSpuIds()).thenReturn(List.of(71L, 72L, 73L, 74L));
        when(spuMapper.listByIds(anyList())).thenReturn(List.of(
                spu(71L, "A", 1, "https://oss/71.png"),
                spu(72L, "B", 1, "https://oss/72.png"),
                spu(73L, "C", 1, "https://oss/73.png"),
                spu(74L, "D", 1, "https://oss/74.png")
        ));

        List<OrbitSpuVO> result = spuService.listOrbit(3, List.of(71L, 72L));

        assertEquals(3, result.size());
        // 候选池内：没展示过的 73/74 优先，随后才用近期商品 71/72 补位
        assertEquals(List.of(73L, 74L),
                result.subList(0, 2).stream().map(OrbitSpuVO::getId).sorted().toList());
        assertTrue(List.of(71L, 72L).contains(result.get(2).getId()));
    }
}
