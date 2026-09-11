package com.stellar.auth;

import com.stellar.dto.MallUserPasswordUpdateDTO;
import com.stellar.entity.MallUser;
import com.stellar.exception.BaseException;
import com.stellar.mapper.MallUserMapper;
import com.stellar.properties.JwtProperties;
import com.stellar.service.LoginAttemptService;
import com.stellar.service.NotificationService;
import com.stellar.service.impl.MallUserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * C 端用户修改密码单元测试（纯 Mockito）。
 * 覆盖：原密码验证改密、邮箱验证码验证改密、原密码错误/验证码错误拒绝、
 * 无任何验证方式拒绝、新密码长度校验、验证码发送对象。
 */
@ExtendWith(MockitoExtension.class)
class MallUserPasswordTest {

    private static final Long UID = 200L;
    private static final String EMAIL = "u@example.com";
    private static final String OLD_PWD = "old_pass_123";
    private static final String NEW_PWD = "new_pass_456";

    @Mock private MallUserMapper mallUserMapper;
    @Mock private JwtProperties jwtProperties;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private NotificationService notificationService;

    @InjectMocks private MallUserServiceImpl userService;

    /** 构造一个已设置原密码的用户 */
    private MallUser existingUser() {
        return MallUser.builder()
                .id(UID)
                .email(EMAIL)
                .status(1)
                .password(BCrypt.hashpw(OLD_PWD, BCrypt.gensalt()))
                .build();
    }

    private MallUserPasswordUpdateDTO dto(String oldPassword, String code, String newPassword) {
        MallUserPasswordUpdateDTO dto = new MallUserPasswordUpdateDTO();
        dto.setOldPassword(oldPassword);
        dto.setCode(code);
        dto.setNewPassword(newPassword);
        return dto;
    }

    @Test
    void updatePassword_byOldPasswordShouldHashAndPersist() {
        when(mallUserMapper.getById(UID)).thenReturn(existingUser());

        userService.updatePassword(UID, dto(OLD_PWD, null, NEW_PWD));

        ArgumentCaptor<MallUser> captor = ArgumentCaptor.forClass(MallUser.class);
        verify(mallUserMapper).update(captor.capture());
        MallUser upd = captor.getValue();
        assertEquals(UID, upd.getId());
        // 落库的是 BCrypt 密文，且能用新密码校验通过
        assertNotEquals(NEW_PWD, upd.getPassword());
        assertTrue(BCrypt.checkpw(NEW_PWD, upd.getPassword()));
    }

    @Test
    void updatePassword_wrongOldPasswordShouldReject() {
        when(mallUserMapper.getById(UID)).thenReturn(existingUser());

        assertThrows(BaseException.class, () -> userService.updatePassword(UID, dto("wrong_pass_999", null, NEW_PWD)));
        verify(mallUserMapper, never()).update(any());
    }

    @Test
    void updatePassword_sameAsOldPasswordShouldReject() {
        when(mallUserMapper.getById(UID)).thenReturn(existingUser());

        assertThrows(BaseException.class, () -> userService.updatePassword(UID, dto(OLD_PWD, null, OLD_PWD)));
        verify(mallUserMapper, never()).update(any());
    }

    @Test
    void updatePassword_byEmailCodeShouldPersist() {
        when(mallUserMapper.getById(UID)).thenReturn(existingUser());
        when(notificationService.verifyEmailCode(EMAIL, "CHANGE_PASSWORD", "123456")).thenReturn(true);

        userService.updatePassword(UID, dto(null, "123456", NEW_PWD));

        ArgumentCaptor<MallUser> captor = ArgumentCaptor.forClass(MallUser.class);
        verify(mallUserMapper).update(captor.capture());
        assertTrue(BCrypt.checkpw(NEW_PWD, captor.getValue().getPassword()));
    }

    @Test
    void updatePassword_invalidEmailCodeShouldReject() {
        when(mallUserMapper.getById(UID)).thenReturn(existingUser());
        when(notificationService.verifyEmailCode(EMAIL, "CHANGE_PASSWORD", "000000")).thenReturn(false);

        assertThrows(BaseException.class, () -> userService.updatePassword(UID, dto(null, "000000", NEW_PWD)));
        verify(mallUserMapper, never()).update(any());
    }

    @Test
    void updatePassword_withoutAnyVerificationShouldReject() {
        when(mallUserMapper.getById(UID)).thenReturn(existingUser());

        assertThrows(BaseException.class, () -> userService.updatePassword(UID, dto(null, null, NEW_PWD)));
        verify(mallUserMapper, never()).update(any());
    }

    @Test
    void updatePassword_tooShortNewPasswordShouldReject() {
        assertThrows(BaseException.class, () -> userService.updatePassword(UID, dto(OLD_PWD, null, "12345")));
        verify(mallUserMapper, never()).update(any());
    }

    @Test
    void updatePassword_unknownUserShouldReject() {
        when(mallUserMapper.getById(UID)).thenReturn(null);

        assertThrows(BaseException.class, () -> userService.updatePassword(UID, dto(OLD_PWD, null, NEW_PWD)));
        verify(mallUserMapper, never()).update(any());
    }

    @Test
    void sendPasswordChangeCode_shouldSendToCurrentEmail() {
        when(mallUserMapper.getById(UID)).thenReturn(existingUser());

        userService.sendPasswordChangeCode(UID);

        verify(notificationService).sendEmailCode(eq(EMAIL), eq("CHANGE_PASSWORD"));
    }
}
