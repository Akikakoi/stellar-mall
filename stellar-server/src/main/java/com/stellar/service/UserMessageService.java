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

    /** 标记已读 */
    void markAsRead(Long id);

    /** 全部标记已读 */
    void markAllAsRead(Long userId);
}
