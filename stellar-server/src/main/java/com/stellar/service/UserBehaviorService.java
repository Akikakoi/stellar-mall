package com.stellar.service;

import com.stellar.entity.UserBehavior;

import java.util.List;

/**
 * 用户行为埋点服务。所有写入异步执行、失败静默，绝不影响业务主流程。
 */
public interface UserBehaviorService {

    /** 异步批量落库（调用方不等待；异常只记日志）。 */
    void trackAsync(List<UserBehavior> rows);
}
