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

@Service
@RequiredArgsConstructor
public class UserMessageServiceImpl implements UserMessageService {

    private final UserMessageMapper userMessageMapper;

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

    @Override
    public PageResult pageByUserId(Long userId, int page, int pageSize) {
        int p = Math.max(page, 1);
        int ps = Math.max(pageSize, 1);
        List<UserMessage> list = userMessageMapper.pageByUserId(userId, (p - 1) * ps, ps);
        long total = userMessageMapper.countByUserId(userId);
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }

    @Override
    public long getUnreadCount(Long userId) {
        return userMessageMapper.countUnread(userId);
    }

    @Override
    public void markAsRead(Long id) {
        userMessageMapper.markAsRead(id);
    }

    @Override
    public void markAllAsRead(Long userId) {
        userMessageMapper.markAllAsRead(userId);
    }
}
