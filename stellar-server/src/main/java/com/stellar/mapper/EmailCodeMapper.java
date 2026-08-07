package com.stellar.mapper;

import com.stellar.entity.EmailCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 邮箱验证码 Mapper。表：stellar_email_code。
 */
@Mapper
public interface EmailCodeMapper {

    int insert(EmailCode emailCode);

    /** 查询指定邮箱、类型、未使用、未过期的最新验证码 */
    EmailCode findLatest(@Param("email") String email,
                         @Param("type") String type);

    /** 标记验证码为已使用 */
    int markUsed(@Param("id") Long id);

    /** 清理过期验证码 */
    int deleteExpired(@Param("before") java.time.LocalDateTime before);
}