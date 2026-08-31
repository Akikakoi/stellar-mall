package com.stellar.service.impl;

import com.stellar.entity.UserMessage;
import com.stellar.mapper.UserMessageMapper;
import com.stellar.result.PageResult;
import com.stellar.service.UserMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户消息服务实现。
 * <p>
 * 提供用户消息的创建、分页查询、未读数量统计、单条/批量标记已读等功能。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UserMessageServiceImpl implements UserMessageService {

    private final UserMessageMapper userMessageMapper;

    /**
     * 创建一条用户消息。
     *
     * @param userId  接收用户ID
     * @param type    消息类型
     * @param title   消息标题
     * @param content 消息内容
     * @param refId   关联业务ID
     */
    @Override
    public void createMessage(Long userId, String type, String title, String content, Long refId) {
        UserMessage msg = UserMessage.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content != null ? content : "")
                .refId(refId)
                .isRead(0)
                .createTime(LocalDateTime.now())
                .build();
        userMessageMapper.insert(msg);
    }

    /**
     * 分页查询用户消息列表。
     *
     * @param userId   用户ID
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Override
    public PageResult pageByUserId(Long userId, int page, int pageSize) {
        int p = Math.max(page, 1);
        int ps = Math.max(pageSize, 1);
        List<UserMessage> list = userMessageMapper.pageByUserId(userId, (p - 1) * ps, ps);
        long total = userMessageMapper.countByUserId(userId);
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }

    /**
     * 获取用户未读消息数量。
     *
     * @param userId 用户ID
     * @return 未读消息数
     */
    @Override
    public long getUnreadCount(Long userId) {
        return userMessageMapper.countUnread(userId);
    }

    /**
     * 将指定消息标记为已读。
     * <p>按 userId + id 双条件更新，消息不属于当前用户时 rows=0 静默跳过，防止横向越权。</p>
     *
     * @param userId 当前登录用户ID
     * @param id     消息ID
     */
    @Override
    public void markAsRead(Long userId, Long id) {
        userMessageMapper.markAsRead(userId, id);
    }

    /**
     * 将用户所有消息标记为已读。
     *
     * @param userId 用户ID
     */
    @Override
    public void markAllAsRead(Long userId) {
        userMessageMapper.markAllAsRead(userId);
    }
}
