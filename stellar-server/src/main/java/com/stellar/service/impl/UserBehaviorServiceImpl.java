package com.stellar.service.impl;

import com.stellar.entity.UserBehavior;
import com.stellar.mapper.UserBehaviorMapper;
import com.stellar.service.UserBehaviorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 埋点落库实现：@Async 异步批量写，异常吞掉只记 warn——埋点丢一条不影响业务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBehaviorServiceImpl implements UserBehaviorService {

    private final UserBehaviorMapper behaviorMapper;

    @Override
    @Async("behaviorExecutor")
    public void trackAsync(List<UserBehavior> rows) {
        if (rows == null || rows.isEmpty()) return;
        try {
            int n = behaviorMapper.batchInsert(rows);
            log.debug("Behavior batch inserted: {} rows", n);
        } catch (Exception e) {
            // 埋点失败绝不外抛：主链路（搜索/下单/积分）不受影响
            log.warn("Behavior batch insert failed (ignored): {}", e.getMessage());
        }
    }
}
