package com.stellar.mapper;

import com.stellar.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMessageMapper {

    int insert(UserMessage message);

    List<UserMessage> pageByUserId(@Param("userId") Long userId,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long countByUserId(@Param("userId") Long userId);

    long countUnread(@Param("userId") Long userId);

    int markAsRead(@Param("id") Long id);

    int markAllAsRead(@Param("userId") Long userId);
}
