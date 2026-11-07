package com.stellar.service.impl;

import com.stellar.entity.MallUser;
import com.stellar.exception.BaseException;
import com.stellar.mapper.MallUserMapper;
import com.stellar.result.PageResult;
import com.stellar.service.AdminUserService;
import com.stellar.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final MallUserMapper mallUserMapper;

    @Override
    public PageResult page(String keyword, Integer status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        long total = mallUserMapper.count(keyword, status);
        List<MallUser> rows = mallUserMapper.page(keyword, status, (safePage - 1) * safeSize, safeSize);
        List<AdminUserVO> records = new ArrayList<>();
        if (rows != null) {
            for (MallUser user : rows) {
                records.add(AdminUserVO.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .nickname(user.getNickname())
                        .status(user.getStatus())
                        .createTime(user.getCreateTime() == null ? null : user.getCreateTime().toString())
                        .build());
            }
        }
        return new PageResult(total, records);
    }

    @Override
    public void setStatus(Long id, Integer status, Long operatorId) {
        if (id == null || (status == null || (status != 0 && status != 1))) {
            throw new BaseException("用户状态参数无效");
        }
        int rows = mallUserMapper.updateStatus(id, status, operatorId);
        if (rows == 0) {
            throw new BaseException("用户不存在");
        }
    }
}