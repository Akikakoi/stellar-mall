package com.stellar.mapper;

import com.stellar.entity.SmsCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SmsCodeMapper {

    int insert(SmsCode smsCode);

    /** 查询指定手机号、类型、未使用、未过期的最新验证码 */
    SmsCode findLatest(@Param("phone") String phone,
                       @Param("type") String type);

    /** 标记验证码为已使用 */
    int markUsed(@Param("id") Long id);

    /** 清理过期验证码 */
    int deleteExpired(@Param("before") java.time.LocalDateTime before);
}
