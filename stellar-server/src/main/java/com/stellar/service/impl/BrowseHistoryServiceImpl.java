package com.stellar.service.impl;

import com.stellar.constant.BrowseHistoryConstant;
import com.stellar.entity.BrowseHistory;
import com.stellar.mapper.BrowseHistoryMapper;
import com.stellar.result.PageResult;
import com.stellar.service.BrowseHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

/**
 * 商品浏览历史服务实现。
 * <p>写入与淘汰在同一事务内串行：upsert 后若该用户记录超过 MAX_KEEP，
 * 删除最旧的 1 条即回到上限之内（本次最多新增 1 条）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrowseHistoryServiceImpl implements BrowseHistoryService {

    private final BrowseHistoryMapper browseHistoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void record(Long userId, Long spuId, Long skuId) {
        if (userId == null || spuId == null) {
            return;
        }
        BrowseHistory history = BrowseHistory.builder()
                .userId(userId)
                .spuId(spuId)
                .skuId(skuId)
                .build();
        browseHistoryMapper.upsert(history);
        // 超出保留上限则淘汰最旧一条（本次最多新增 1 条，故只需删 1 条）
        browseHistoryMapper.deleteOverLimit(userId, BrowseHistoryConstant.MAX_KEEP);
    }

    @Override
    public PageResult page(Long userId, int page, int pageSize) {
        if (userId == null) {
            return new PageResult(0L, new ArrayList<>());
        }
        int p = Math.max(page, 1);
        int ps = Math.max(pageSize, 1);
        long total = browseHistoryMapper.countByUserId(userId);
        return new PageResult(total,
                browseHistoryMapper.page(userId, (p - 1) * ps, ps));
    }

    @Override
    public void deleteById(Long userId, Long id) {
        if (userId == null || id == null) {
            return;
        }
        browseHistoryMapper.deleteById(userId, id);
    }

    @Override
    public void clear(Long userId) {
        if (userId == null) {
            return;
        }
        browseHistoryMapper.clearByUserId(userId);
    }
}