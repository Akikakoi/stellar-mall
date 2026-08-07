package com.stellar.mapper;

import com.stellar.entity.NotificationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通知日志 Mapper。表：stellar_notification_log。
 */
@Mapper
public interface NotificationLogMapper {

    int insert(NotificationLog log);

    List<NotificationLog> page(@Param("userId") Long userId,
                               @Param("type") String type,
                               @Param("channel") String channel,
                               @Param("offset") int offset,
                               @Param("pageSize") int pageSize);

    long count(@Param("userId") Long userId,
               @Param("type") String type,
               @Param("channel") String channel);
}
