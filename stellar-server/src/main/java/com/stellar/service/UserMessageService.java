package com.stellar.service;

import com.stellar.entity.UserMessage;
import com.stellar.result.PageResult;

public interface UserMessageService {

    /** 创建一条消息 */
    void createMessage(Long userId, String type, String title, String content, Long refId);

    /** 分页查询用户消息 */
    PageResult pageByUserId(Long userId, int page, int pageSize);

    /** 获取未读数量 */
    long getUnreadCount(Long userId);

    /** 标记已读（按 userId + id 双条件，防止横向越权） */
    void markAsRead(Long userId, Long id);

    /** 全部标记已读 */
    void markAllAsRead(Long userId);

    /** 清空所有已读消息 */
    void deleteRead(Long userId);
}
