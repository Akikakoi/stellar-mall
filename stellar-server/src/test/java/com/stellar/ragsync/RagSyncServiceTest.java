package com.stellar.ragsync;

import com.stellar.dto.CategorySaveDTO;
import com.stellar.dto.SpuSaveDTO;
import com.stellar.entity.Category;
import com.stellar.entity.RagSyncOutbox;
import com.stellar.entity.Sku;
import com.stellar.ragsync.client.RagSyncClient;
import com.stellar.ragsync.service.RagSyncService;
import com.stellar.ragsync.mapper.RagSyncOutboxMapper;
import com.stellar.service.CategoryService;
import com.stellar.service.SpuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P1-M1 RED阶段：SPU 变更 → 落 outbox → 同步 RAG（3 次重试 + 失败标记）。
 * ⚠️ 类级别 @Transactional：每个 @Test 结束后回滚，不会残留 outbox/SPU。
 * ⚠️ 每个测试先造真实 SPU（动态 ID），保证 spuService.getById(bizId) 非空，
 *    processPendingOne 才能进入 ragSyncClient.syncSpu(mock) 分支。
 */
@SpringBootTest(properties = "stellar.elasticsearch.enabled=false")
@Transactional
class RagSyncServiceTest {

    @Autowired(required = false)
    private RagSyncService ragSyncService;
    @Autowired(required = false)
    private RagSyncOutboxMapper outboxMapper;
    @Autowired(required = false)
    private SpuService spuService;
    @Autowired(required = false)
    private CategoryService categoryService;

    @MockBean
    private RagSyncClient ragSyncClient;

    /** ES 关闭后 ElasticsearchConfig 不再创建 ElasticsearchOperations，
     *  用 Mock 满足 SpuEsSyncService 等的构造注入，确保测试不触碰真实 ES。 */
    @MockBean
    private org.springframework.data.elasticsearch.core.ElasticsearchOperations elasticsearchOperations;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(ragSyncClient);
        when(ragSyncClient.syncDoc(any())).thenReturn(true); // 默认 stub 避免 NPE
    }

    private static String uid(String prefix) {
        return prefix + "-UT-" + Integer.toHexString(
                (int) ((System.nanoTime() ^ System.identityHashCode(Thread.currentThread())) & 0xffff));
    }

    /** 创建一级分类 → 保存 1 个空 SKU 列表的 SPU，返回动态 spuId */
    private Long seedSpuId() {
        Category c1 = new Category();
        c1.setName(uid("RagL1"));
        c1.setParentId(0L); c1.setLevel(1); c1.setStatus(1);
        CategorySaveDTO cdto1 = new CategorySaveDTO();
        BeanUtils.copyProperties(c1, cdto1);
        Long l1Id = categoryService.save(cdto1);

        SpuSaveDTO dto = new SpuSaveDTO();
        dto.setName(uid("RagSpu"));
        dto.setCategoryId(l1Id);
        dto.setCategory2Id(l1Id);
        dto.setStatus(1);
        dto.setDescriptionMd("# RAG 测试 SPU");
        dto.setSkuList(Collections.emptyList());
        return spuService.saveWithSkus(dto);
    }

    @Test
    void enqueueSpuSync_insertsOutbox_withPendingState() {
        assertNotNull(ragSyncService, "RED失败：RagSyncService 未注册");
        assertNotNull(outboxMapper, "RED失败：RagSyncOutboxMapper 未注册");

        Long outboxId = ragSyncService.enqueueSpuSync(42L, "TEST_OP_SAVE");
        assertNotNull(outboxId);

        RagSyncOutbox box = outboxMapper.getById(outboxId);
        assertNotNull(box);
        assertEquals("SPU", box.getBizType());
        assertEquals(Long.valueOf(42L), box.getBizId());
        assertEquals("TEST_OP_SAVE", box.getOpType());
        assertEquals(Integer.valueOf(0), box.getSynced()); // 0=未同步
        assertEquals(Integer.valueOf(0), box.getFailed()); // 0=未失败
        assertEquals(Integer.valueOf(0), box.getRetryCount());
        assertNotNull(box.getCreateTime());
    }

    @Test
    void processPending_whenRagClientSuccess_marksSyncedAndIncrementsRetryCount() throws Exception {
        assertNotNull(ragSyncService);
        assertNotNull(spuService);
        when(ragSyncClient.syncSpu(any())).thenReturn(true);

        Long spuId = seedSpuId();
        Long boxId = ragSyncService.enqueueSpuSync(spuId, "SAVE");
        ragSyncService.processPendingOne(boxId);

        RagSyncOutbox box = outboxMapper.getById(boxId);
        assertEquals(Integer.valueOf(1), box.getSynced(), "同步成功 → synced=1");
        assertEquals(Integer.valueOf(0), box.getFailed(), "同步成功 → failed=0");
        assertEquals(Integer.valueOf(1), box.getRetryCount(), "retryCount 必须 = 实际尝试次数 1");
        assertNotNull(box.getLastTryTime());
        verify(ragSyncClient, times(1)).syncSpu(any());
    }

    @Test
    void processPending_3TimesFail_thenMarksFailed_stopsRetrying() throws Exception {
        assertNotNull(ragSyncService);
        assertNotNull(spuService);
        when(ragSyncClient.syncSpu(any()))
                .thenThrow(new RuntimeException("RAG 挂了 1"))
                .thenThrow(new RuntimeException("RAG 挂了 2"))
                .thenThrow(new RuntimeException("RAG 挂了 3"));

        Long spuId = seedSpuId();
        Long boxId = ragSyncService.enqueueSpuSync(spuId, "SAVE");

        // 手动跑 3 次
        for (int i = 0; i < 3; i++) ragSyncService.processPendingOne(boxId);

        RagSyncOutbox box = outboxMapper.getById(boxId);
        assertEquals(Integer.valueOf(0), box.getSynced());
        assertEquals(Integer.valueOf(1), box.getFailed(), "3 次失败 → failed=1，不再重试");
        assertEquals(Integer.valueOf(3), box.getRetryCount(), "实际尝试 3 次");
        assertTrue(box.getLastErrorMsg().contains("RAG 挂了"),
                "lastErrorMsg 要记录最后一次异常信息，实际: " + box.getLastErrorMsg());
        verify(ragSyncClient, times(3)).syncSpu(any());

        // 再跑第 4 次 → 不应该再调 client
        Mockito.reset(ragSyncClient);
        when(ragSyncClient.syncSpu(any())).thenReturn(true);
        ragSyncService.processPendingOne(boxId);
        verify(ragSyncClient, times(0)).syncSpu(any());
    }
}
