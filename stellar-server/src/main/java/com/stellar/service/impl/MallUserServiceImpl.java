package com.stellar.service.impl;

import com.stellar.constant.JwtClaimsConstant;
import com.stellar.constant.MessageConstant;
import com.stellar.dto.MallUserLoginDTO;
import com.stellar.dto.MallUserProfileUpdateDTO;
import com.stellar.entity.MallUser;
import com.stellar.exception.BaseException;
import com.stellar.exception.LoginFailedException;
import com.stellar.mapper.MallUserMapper;
import com.stellar.properties.JwtProperties;
import com.stellar.service.MallUserService;
import com.stellar.utils.JwtUtil;
import com.stellar.vo.MallUserLoginVO;
import com.stellar.vo.MallUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MallUserServiceImpl implements MallUserService {

    private final MallUserMapper mallUserMapper;
    private final JwtProperties jwtProperties;

    @Override
    public MallUserLoginVO login(MallUserLoginDTO dto) {
        if (dto == null || dto.getPhone() == null || dto.getPassword() == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        MallUser user = mallUserMapper.getByPhone(dto.getPhone());
        if (user == null) {
            user = MallUser.builder()
                    .phone(dto.getPhone())
                    .nickname("用户" + dto.getPhone().substring(dto.getPhone().length() - 4))
                    .password(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()))
                    .status(1)
                    .build();
            mallUserMapper.insert(user);
        } else {
            if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
                throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
            }
            if (user.getStatus() == null || user.getStatus() != 1) {
                throw new BaseException(MessageConstant.ACCOUNT_LOCKED);
            }
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );

        return MallUserLoginVO.builder()
                .userId(user.getId())
                .token(token)
                .build();
    }

    @Override
    public MallUser getById(Long id) {
        return id == null ? null : mallUserMapper.getById(id);
    }

    @Override
    public MallUserVO getProfile(Long id) {
        if (id == null) return null;
        MallUser u = mallUserMapper.getById(id);
        if (u == null) return null;
        return MallUserVO.builder()
                .id(u.getId())
                .phone(u.getPhone())
                .nickname(u.getNickname())
                .status(u.getStatus())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long id, MallUserProfileUpdateDTO dto) {
        if (id == null || dto == null) return;
        boolean changed = false;
        MallUser upd = new MallUser();
        upd.setId(id);
        if (dto.getNickname() != null) {
            upd.setNickname(dto.getNickname());
            changed = true;
        }
        if (changed) {
            mallUserMapper.update(upd);
        }
    }

    @Override
    public MallUserLoginVO loginOrRegisterByPhone(String phone) {
        MallUser user = mallUserMapper.getByPhone(phone);
        if (user == null) {
            user = MallUser.builder()
                    .phone(phone)
                    .nickname("用户" + phone.substring(phone.length() - 4))
                    .password(BCrypt.hashpw("sms_" + System.currentTimeMillis(), BCrypt.gensalt()))
                    .status(1)
                    .build();
            mallUserMapper.insert(user);
        } else {
            if (user.getStatus() == null || user.getStatus() != 1) {
                throw new BaseException(MessageConstant.ACCOUNT_LOCKED);
            }
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );

        return MallUserLoginVO.builder()
                .userId(user.getId())
                .token(token)
                .build();
    }
}
