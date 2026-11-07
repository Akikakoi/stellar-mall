package com.stellar.mapper;

import com.stellar.annotation.AutoFill;
import com.stellar.entity.MallUser;
import com.stellar.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * C 端用户 Mapper。表：stellar_mall_user。
 */
@Mapper
public interface MallUserMapper {

    @AutoFill(OperationType.INSERT)
    int insert(MallUser user);

    @AutoFill(OperationType.UPDATE)
    int update(MallUser user);

    MallUser getById(@Param("id") Long id);

    MallUser getByEmail(@Param("email") String email);

    /** 导出：查询全部用户。 */
    List<MallUser> listAllForExport();

    /** 管理端分页查询 C 端用户（keyword 匹配邮箱/昵称）。 */
    List<MallUser> page(@Param("keyword") String keyword,
                        @Param("status") Integer status,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

    /** 管理端统计 C 端用户数量。 */
    long count(@Param("keyword") String keyword, @Param("status") Integer status);

    /** 管理端启用/锁定用户。 */
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("updateUser") Long updateUser);
}