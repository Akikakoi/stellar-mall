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

    MallUser getByPhone(@Param("phone") String phone);

    MallUser getByEmail(@Param("email") String email);

    /** 导出：查询全部用户。 */
    List<MallUser> listAllForExport();
}