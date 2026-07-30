package com.stellar.service;

import com.stellar.dto.AddressSaveDTO;
import com.stellar.dto.AddressUpdateDTO;
import com.stellar.vo.AddressVO;

import java.util.List;

/**
 * 收货地址服务。
 */
public interface AddressService {

    /**
     * 新增地址。
     *
     * @param dto 地址信息
     * @return 新地址 ID
     */
    Long save(AddressSaveDTO dto);

    /**
     * 更新地址。
     *
     * @param dto 地址信息
     */
    void update(AddressUpdateDTO dto);

    /**
     * 删除地址。
     *
     * @param id     地址 ID
     * @param userId 当前用户 ID
     */
    void deleteById(Long id, Long userId);

    /**
     * 按 ID 查询地址。
     *
     * @param id     地址 ID
     * @param userId 当前用户 ID
     * @return 地址 VO
     */
    AddressVO getById(Long id, Long userId);

    /**
     * 查询当前用户的地址列表。
     *
     * @param userId 当前用户 ID
     * @return 地址列表，默认地址置顶，再按创建时间倒序
     */
    List<AddressVO> listByUser(Long userId);

    /**
     * 设为默认地址。
     *
     * @param id     地址 ID
     * @param userId 当前用户 ID
     */
    void setDefault(Long id, Long userId);
}
