package com.stellar.service.impl;

import com.stellar.context.BaseContext;
import com.stellar.dto.AddressSaveDTO;
import com.stellar.dto.AddressUpdateDTO;
import com.stellar.entity.Address;
import com.stellar.exception.BaseException;
import com.stellar.mapper.AddressMapper;
import com.stellar.service.AddressService;
import com.stellar.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 收货地址服务实现。
 * <p>
 * 功能与原先保持一致：增删改查、设为默认、默认地址唯一性。
 * 改进点：
 * 1. 使用 DTO 接收请求参数，避免直接暴露实体审计字段；
 * 2. 所有涉及单条地址的操作都校验用户所有权，防止横向越权；
 * 3. 增加服务端字段校验（手机号格式、字段长度等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(AddressSaveDTO dto) {
        if (dto == null) {
            throw new BaseException("地址信息不能为空");
        }

        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BaseException("用户未登录");
        }

        Address address = new Address();
        BeanUtils.copyProperties(dto, address);
        address.setUserId(userId);
        address.setIsDefault(normalizeIsDefault(dto.getIsDefault()));
        address.setCreateTime(LocalDateTime.now());
        address.setCreateUser(userId);
        address.setUpdateTime(LocalDateTime.now());
        address.setUpdateUser(userId);

        // 如果设为默认，先清空当前用户所有默认地址
        if (address.getIsDefault() == 1) {
            addressMapper.clearDefault(userId);
        }

        addressMapper.insert(address);
        return address.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AddressUpdateDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BaseException("地址信息不能为空");
        }

        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BaseException("用户未登录");
        }

        // 校验所有权
        Address existing = addressMapper.getById(dto.getId(), userId);
        if (existing == null) {
            throw new BaseException("地址不存在或无权操作");
        }

        Address address = new Address();
        BeanUtils.copyProperties(dto, address);
        address.setUserId(userId);
        address.setIsDefault(normalizeIsDefault(dto.getIsDefault()));
        address.setUpdateTime(LocalDateTime.now());
        address.setUpdateUser(userId);

        // 如果设为默认，先清空当前用户所有默认地址
        if (address.getIsDefault() == 1) {
            addressMapper.clearDefault(userId);
        }

        addressMapper.update(address.getId(), userId, address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id, Long userId) {
        if (id == null || userId == null) {
            throw new BaseException("参数非法");
        }

        // 校验所有权：按 ID + userId 删除，删除行数为 0 表示不存在或无权操作
        int affected = addressMapper.deleteById(id, userId);
        if (affected == 0) {
            log.warn("用户 [{}] 尝试删除不属于自己的地址 [{}]", userId, id);
            throw new BaseException("地址不存在或无权操作");
        }
    }

    @Override
    public AddressVO getById(Long id, Long userId) {
        if (id == null || userId == null) {
            throw new BaseException("参数非法");
        }

        Address address = addressMapper.getById(id, userId);
        if (address == null) {
            throw new BaseException("地址不存在或无权操作");
        }
        return toVO(address);
    }

    @Override
    public List<AddressVO> listByUser(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        List<Address> addresses = addressMapper.listByUserId(userId);
        if (addresses == null || addresses.isEmpty()) {
            return Collections.emptyList();
        }
        return addresses.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id, Long userId) {
        if (id == null || userId == null) {
            throw new BaseException("参数非法");
        }

        // 先校验所有权：查询当前用户的该条地址
        Address existing = addressMapper.getById(id, userId);
        if (existing == null) {
            throw new BaseException("地址不存在或无权操作");
        }

        // 已经是默认地址则无需操作
        if (Objects.equals(existing.getIsDefault(), 1)) {
            return;
        }

        addressMapper.clearDefault(userId);
        int affected = addressMapper.setDefault(id, userId);
        if (affected == 0) {
            throw new BaseException("设置默认地址失败");
        }
    }

    /**
     * 将 {@link Address} 转换为 {@link AddressVO}。
     */
    private AddressVO toVO(Address address) {
        if (address == null) {
            return null;
        }
        AddressVO vo = new AddressVO();
        BeanUtils.copyProperties(address, vo);
        return vo;
    }

    /**
     * 规范化 isDefault 字段，仅允许 0 或 1。
     */
    private Integer normalizeIsDefault(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }
}
