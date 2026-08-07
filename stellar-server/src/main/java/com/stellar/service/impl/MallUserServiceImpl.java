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

/**
 * 商城用户服务实现。
 * <p>
 * 提供用户登录（密码登录 / 邮箱验证码一键登录）、用户注册、个人信息查询与更新等功能。
 * 新用户首次登录时自动注册。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallUserServiceImpl implements MallUserService {

    private final MallUserMapper mallUserMapper;
    private final JwtProperties jwtProperties;

    /**
     * 用户邮箱 + 密码登录；新用户首次登录时自动注册。
     *
     * @param dto 登录参数（邮箱、密码）
     * @return 登录结果（含用户ID和JWT Token）
     * @throws LoginFailedException 密码错误时抛出
     * @throws BaseException       账号被禁用时抛出
     */
    @Override
    public MallUserLoginVO login(MallUserLoginDTO dto) {
        if (dto == null || dto.getEmail() == null || dto.getPassword() == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        MallUser user = mallUserMapper.getByEmail(dto.getEmail());
        if (user == null) {
            user = MallUser.builder()
                    .email(dto.getEmail())
                    .nickname(dto.getEmail().substring(0, dto.getEmail().indexOf('@')))
                    .password(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()))
                    .status(1)
                    .build();
            mallUserMapper.insert(user);
        } else {
            if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
                throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
            }
            checkAccountStatus(user);
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

    /**
     * 根据ID查询用户实体。
     *
     * @param id 用户ID
     * @return 用户实体，不存在时返回 null
     */
    @Override
    public MallUser getById(Long id) {
        return id == null ? null : mallUserMapper.getById(id);
    }

    /**
     * 查询用户个人信息（脱敏后）。
     *
     * @param id 用户ID
     * @return 用户个人信息VO，不存在时返回 null
     */
    @Override
    public MallUserVO getProfile(Long id) {
        if (id == null) return null;
        MallUser u = mallUserMapper.getById(id);
        if (u == null) return null;
        return MallUserVO.builder()
                .id(u.getId())
                .phone(u.getPhone())
                .email(u.getEmail())
                .nickname(u.getNickname())
                .status(u.getStatus())
                .build();
    }

    /**
     * 更新用户个人信息（昵称等）。
     *
     * @param id  用户ID
     * @param dto 更新参数
     */
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

    /**
     * 邮箱验证码一键登录或注册。已有用户直接登录，新用户自动注册。
     *
     * @param email 邮箱地址
     * @return 登录结果（含用户ID和JWT Token）
     * @throws BaseException 账号被禁用时抛出
     */
    @Override
    public MallUserLoginVO loginOrRegisterByEmail(String email) {
        MallUser user = mallUserMapper.getByEmail(email);
        if (user == null) {
            user = MallUser.builder()
                    .email(email)
                    .nickname(email.substring(0, email.indexOf('@')))
                    .password(BCrypt.hashpw("email_" + System.currentTimeMillis(), BCrypt.gensalt()))
                    .status(1)
                    .build();
            mallUserMapper.insert(user);
        } else {
            checkAccountStatus(user);
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

    /**
     * 注销当前账号：将账号状态置为已注销（status=2）。
     * 注销后该账号无法再登录，历史订单等数据保留。
     *
     * @param id 用户ID
     * @throws BaseException 用户不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deactivateAccount(Long id) {
        if (id == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        MallUser user = mallUserMapper.getById(id);
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        MallUser upd = new MallUser();
        upd.setId(id);
        upd.setStatus(2);
        mallUserMapper.update(upd);
    }

    /**
     * 校验账号状态：已注销（status=2）或已锁定（status!=1）时禁止登录。
     */
    private void checkAccountStatus(MallUser user) {
        if (user.getStatus() != null && user.getStatus() == 2) {
            throw new BaseException(MessageConstant.ACCOUNT_CLOSED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BaseException(MessageConstant.ACCOUNT_LOCKED);
        }
    }
}