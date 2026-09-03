package com.stellar.mapper;

import com.stellar.entity.UserBehavior;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户行为埋点 Mapper。表：stellar_user_behavior（只追加）。
 */
public interface UserBehaviorMapper {

    /** 批量插入（event_time 统一取数据库 NOW()，批次内先后由自增 id 区分）。 */
    int batchInsert(@Param("list") List<UserBehavior> list);
}
