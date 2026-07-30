package com.stellar.mapper;

import com.stellar.entity.Address;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AddressMapper {
    int insert(Address address);

    /**
     * 按 ID + 用户 ID 更新，防止横向越权。
     */
    int update(@Param("id") Long id, @Param("userId") Long userId, @Param("address") Address address);

    /**
     * 按 ID + 用户 ID 删除，防止横向越权。
     */
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 按 ID + 用户 ID 查询，防止横向越权。
     */
    Address getById(@Param("id") Long id, @Param("userId") Long userId);

    List<Address> listByUserId(@Param("userId") Long userId);

    int clearDefault(@Param("userId") Long userId);

    /**
     * 按 ID + 用户 ID 设为默认，防止横向越权。
     */
    int setDefault(@Param("id") Long id, @Param("userId") Long userId);
}