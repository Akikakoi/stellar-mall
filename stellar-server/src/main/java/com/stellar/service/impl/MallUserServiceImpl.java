package com.stellar.service.impl;

import com.stellar.constant.JwtClaimsConstant;
import com.stellar.constant.MessageConstant;
import com.stellar.dto.MallUserLoginDTO;
import com.stellar.dto.MallUserPasswordUpdateDTO;
import com.stellar.dto.MallUserProfileUpdateDTO;
import com.stellar.entity.MallUser;
import com.stellar.exception.BaseException;
import com.stellar.exception.LoginFailedException;
import com.stellar.mapper.MallUserMapper;
import com.stellar.properties.JwtProperties;
import com.stellar.service.MallUserService;
import com.stellar.service.LoginAttemptService;
import com.stellar.service.NotificationService;
import com.stellar.utils.JwtUtil;
import com.stellar.vo.MallUserLoginVO;
import com.stellar.vo.MallUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    /** Redis key 前缀：refresh:mall_user:{id}，单设备登录时新登录覆盖旧 refresh */
    private static final String REFRESH_KEY_PREFIX = "refresh:mall_user:";

    /** 换绑邮箱验证码类型（stellar_email_code.type） */
    private static final String EMAIL_CHANGE_CODE_TYPE = "CHANGE_EMAIL";

    /** 修改密码验证码类型（stellar_email_code.type） */
    private static final String PASSWORD_CHANGE_CODE_TYPE = "CHANGE_PASSWORD";

    /** 新密码长度下限 / 上限（与前端提示保持一致） */
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 32;

    private final MallUserMapper mallUserMapper;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final LoginAttemptService loginAttemptService;
    private final NotificationService notificationService;

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
        // E2: 登录前检查账号是否被临时锁定（失败次数过多）
        loginAttemptService.checkLocked("mall_user", dto.getEmail());

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
                loginAttemptService.recordFailure("mall_user", dto.getEmail());
                throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
            }
            checkAccountStatus(user);
        }

        // E2: 登录成功，清零失败计数
        loginAttemptService.clearAttempts("mall_user", dto.getEmail());

        return issueTokens(user);
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

        return issueTokens(user);
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
     * 发送换绑邮箱验证码到新邮箱。
     * 校验：新邮箱不与当前邮箱相同、未被其他账号注册；通过后发送 CHANGE_EMAIL 类型验证码。
     *
     * @param userId   当前登录用户 ID
     * @param newEmail 新邮箱地址
     * @throws BaseException 邮箱非法、与当前相同或已被注册时抛出
     */
    @Override
    public com.stellar.entity.EmailCode sendEmailChangeCode(Long userId, String newEmail) {
        if (userId == null || newEmail == null || newEmail.isBlank()) {
            throw new BaseException("请输入新邮箱地址");
        }
        MallUser current = mallUserMapper.getById(userId);
        if (current == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        if (newEmail.equalsIgnoreCase(current.getEmail())) {
            throw new BaseException("新邮箱不能与当前邮箱相同");
        }
        assertEmailAvailable(newEmail, userId);
        return notificationService.sendEmailCode(newEmail, EMAIL_CHANGE_CODE_TYPE);
    }

    /**
     * 校验验证码并更换登录邮箱。
     * 顺序：先做新邮箱占用终审（避免占用错误白白消费验证码），再校验验证码（通过即标记已用防重放），最后更新邮箱。
     *
     * @param userId   当前登录用户 ID
     * @param newEmail 新邮箱地址
     * @param code     用户输入的邮箱验证码
     * @throws BaseException 邮箱已被注册或验证码错误时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeEmail(Long userId, String newEmail, String code) {
        if (userId == null || newEmail == null || newEmail.isBlank() || code == null || code.isBlank()) {
            throw new BaseException("参数不完整");
        }
        MallUser current = mallUserMapper.getById(userId);
        if (current == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        // 1. 最终校验：新邮箱未被其他账号注册（权威校验，防止发送验证码后被他人抢注）
        assertEmailAvailable(newEmail, userId);
        // 2. 校验验证码（校验通过即标记已使用，防重放）
        if (!notificationService.verifyEmailCode(newEmail, EMAIL_CHANGE_CODE_TYPE, code)) {
            throw new BaseException("验证码错误或已过期");
        }
        // 3. 更新登录邮箱
        MallUser upd = new MallUser();
        upd.setId(userId);
        upd.setEmail(newEmail);
        mallUserMapper.update(upd);
        log.info("[换绑邮箱] 用户 {} 登录邮箱已由 {} 变更为 {}", userId, current.getEmail(), newEmail);
    }

    /**
     * 发送修改密码验证码到当前登录邮箱。
     * 用于邮箱验证码注册（从未设置过密码）的账号自助设置密码。
     *
     * @param userId 当前登录用户 ID
     * @return 持久化后的验证码实体（开发模式下 Controller 用其 code 作为 devCode 返回）
     * @throws BaseException 用户不存在时抛出
     */
    @Override
    public com.stellar.entity.EmailCode sendPasswordChangeCode(Long userId) {
        if (userId == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        MallUser user = mallUserMapper.getById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        return notificationService.sendEmailCode(user.getEmail(), PASSWORD_CHANGE_CODE_TYPE);
    }

    /**
     * 修改/设置登录密码。
     * <p>
     * 验证方式二选一（都传时以邮箱验证码为准）：<br>
     * 1. 原密码：BCrypt 校验 oldPassword；<br>
     * 2. 邮箱验证码：校验发往当前登录邮箱的 code（校验通过即标记已用，防重放）。
     * </p>
     * 校验通过后用 BCrypt 加密写入新密码；明文密码不落库、不写日志。
     *
     * @param userId 当前登录用户 ID
     * @param dto    改密参数
     * @throws BaseException 用户不存在 / 原密码错误 / 验证码错误 / 新密码不合规时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long userId, MallUserPasswordUpdateDTO dto) {
        if (userId == null || dto == null) {
            throw new BaseException("参数不完整");
        }
        String newPassword = dto.getNewPassword() == null ? "" : dto.getNewPassword().trim();
        if (newPassword.length() < MIN_PASSWORD_LENGTH || newPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new BaseException("密码长度需为 " + MIN_PASSWORD_LENGTH + "-" + MAX_PASSWORD_LENGTH + " 位");
        }
        MallUser user = mallUserMapper.getById(userId);
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        boolean byEmailCode = dto.getCode() != null && !dto.getCode().isBlank();
        boolean byOldPassword = dto.getOldPassword() != null && !dto.getOldPassword().isBlank();
        if (!byEmailCode && !byOldPassword) {
            throw new BaseException("请输入原密码或获取邮箱验证码");
        }

        if (byEmailCode) {
            // 邮箱验证码校验（校验通过即标记已使用，防重放）
            if (!notificationService.verifyEmailCode(user.getEmail(), PASSWORD_CHANGE_CODE_TYPE, dto.getCode().trim())) {
                throw new BaseException("验证码错误或已过期");
            }
        } else {
            if (user.getPassword() == null || !BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
                throw new BaseException(MessageConstant.PASSWORD_ERROR);
            }
            if (dto.getOldPassword().equals(newPassword)) {
                throw new BaseException("新密码不能与原密码相同");
            }
        }

        MallUser upd = new MallUser();
        upd.setId(userId);
        upd.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        mallUserMapper.update(upd);
        // 安全：不记录明文密码，仅记录验证方式
        log.info("[修改密码] 用户 {} 密码已更新，验证方式：{}", userId, byEmailCode ? "邮箱验证码" : "原密码");
    }

    /**
     * 校验邮箱未被其他账号注册；被占用时抛出"该邮箱已被注册"。
     */
    private void assertEmailAvailable(String email, Long selfId) {
        MallUser exist = mallUserMapper.getByEmail(email);
        if (exist != null && !exist.getId().equals(selfId)) {
            throw new BaseException("该邮箱已被注册");
        }
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

    /**
     * 签发 access + refresh token 并写入 Redis（单设备登录覆盖）。
     */
    private MallUserLoginVO issueTokens(MallUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());

        String accessToken = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );
        String refreshToken = JwtUtil.createRefreshJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserRefreshTtl(),
                claims
        );
        // 单设备登录：新 refresh 覆盖旧 refresh
        stringRedisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getId(),
                refreshToken,
                jwtProperties.getUserRefreshTtl(),
                TimeUnit.MILLISECONDS
        );
        return MallUserLoginVO.builder()
                .userId(user.getId())
                .token(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    /**
     * 用 refresh token 换新的 access + refresh token。
     * 校验：token 可解析 + type=refresh + Redis 存的 token 与传入一致（单设备 + 一次性使用）。
     */
    @Override
    public MallUserLoginVO refresh(String refreshToken) {
        io.jsonwebtoken.Claims claims;
        try {
            claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), refreshToken);
        } catch (Exception e) {
            log.warn("[MallUserService] refresh token 解析失败: {}", e.getMessage());
            throw new BaseException("refresh token 无效或已过期");
        }
        String type = claims.get(JwtClaimsConstant.TOKEN_TYPE, String.class);
        if (!JwtUtil.TYPE_REFRESH.equals(type)) {
            throw new BaseException("refresh token 无效或已过期");
        }
        Long userId = ((Number) claims.get(JwtClaimsConstant.USER_ID)).longValue();

        String stored = stringRedisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
        if (stored == null || !stored.equals(refreshToken)) {
            throw new BaseException("refresh token 无效或已过期");
        }

        MallUser user = mallUserMapper.getById(userId);
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        checkAccountStatus(user);

        return issueTokens(user);
    }
}