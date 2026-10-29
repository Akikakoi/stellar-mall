package com.stellar.task.expire;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 订单过期延迟队列：基于 Redis ZSET 的到期调度。
 *
 * <p>member = orderId，score = 到期毫秒。取出走 Lua 脚本原子「查 + 删」，
 * 多实例并发扫描不会重复取到同一条；数据落在 Redis（compose 里开了 AOF），
 * 服务重启后会继续把已到期的任务捞出来，不会丢。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireDelayQueue {

    /**
     * 原子取出并移除已到期成员。
     * <p>查（ZRANGEBYSCORE）与删（ZREM）必须在同一脚本里完成，否则两个实例会取到同一条。
     * 返回值用逗号拼接成单个字符串：Lua 返回 table 时 Spring 的序列化行为随版本而变，
     * 拼成字符串最稳。</p>
     */
    private static final String POP_DUE_LUA =
            "local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2]) "
                    + "if #due > 0 then redis.call('ZREM', KEYS[1], unpack(due)) end "
                    + "if #due == 0 then return '' end "
                    + "return table.concat(due, ',')";

    private static final DefaultRedisScript<String> POP_DUE_SCRIPT =
            new DefaultRedisScript<>(POP_DUE_LUA, String.class);

    private final StringRedisTemplate redis;

    private final OrderExpireProperties properties;

    /** 按配置的超时分钟数入队。 */
    public void schedule(Long orderId) {
        scheduleAt(orderId, System.currentTimeMillis() + properties.getDelayMinutes() * 60_000L);
    }

    /** 指定到期毫秒入队（便于测试与补投）。 */
    public void scheduleAt(Long orderId, long dueAtMillis) {
        if (orderId == null) {
            return;
        }
        try {
            redis.opsForZSet().add(properties.getDelayKey(), String.valueOf(orderId), dueAtMillis);
        } catch (Exception e) {
            // Redis 抖动不能反噬已提交的下单主流程：漏掉的延迟任务由低频对账扫描兜底
            log.error("[OrderExpireDelayQueue] 入队失败，将由对账扫描兜底: orderId={}", orderId, e);
        }
    }

    /**
     * 事务提交后再入队；无事务时立即入队。
     * <p>订单回滚不入队——否则会去取消一笔根本不存在的订单。</p>
     */
    public void scheduleAfterCommit(Long orderId) {
        if (orderId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule(orderId);
                }
            });
            return;
        }
        schedule(orderId);
    }

    /** 原子取出并移除已到期（score &lt;= nowMillis）的订单 id，最多 limit 个。 */
    public List<Long> popDue(long nowMillis, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        String result = redis.execute(POP_DUE_SCRIPT, Collections.singletonList(properties.getDelayKey()),
                String.valueOf(nowMillis), String.valueOf(limit));
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = result.split(",");
        List<Long> due = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isEmpty()) {
                due.add(Long.valueOf(part));
            }
        }
        return due;
    }
}
