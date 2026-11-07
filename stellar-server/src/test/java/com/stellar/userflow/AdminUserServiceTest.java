package com.stellar.userflow;

import com.stellar.entity.MallUser;
import com.stellar.exception.BaseException;
import com.stellar.mapper.MallUserMapper;
import com.stellar.result.PageResult;
import com.stellar.service.impl.AdminUserServiceImpl;
import com.stellar.vo.AdminUserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService — C 端用户管理")
class AdminUserServiceTest {

    @Mock private MallUserMapper mallUserMapper;
    @InjectMocks private AdminUserServiceImpl service;

    @Test
    @DisplayName("分页映射为不含密码的 VO")
    void page_mapsSafeFields() {
        MallUser user = MallUser.builder().id(1L).email("a@b.com").nickname("测试")
                .password("secret-hash").status(1).createTime(LocalDateTime.of(2026, 1, 2, 3, 4)).build();
        when(mallUserMapper.count("a", 1)).thenReturn(1L);
        when(mallUserMapper.page("a", 1, 0, 20)).thenReturn(Collections.singletonList(user));

        PageResult result = service.page("a", 1, 1, 20);

        assertEquals(1L, result.getTotal());
        AdminUserVO vo = (AdminUserVO) result.getRecords().get(0);
        assertEquals("a@b.com", vo.getEmail());
        assertEquals("测试", vo.getNickname());
        assertEquals("2026-01-02T03:04", vo.getCreateTime());
    }

    @Test
    @DisplayName("非法状态 → 拒绝")
    void setStatus_invalid_rejected() {
        assertThrows(BaseException.class, () -> service.setStatus(1L, 9, 9L));
    }

    @Test
    @DisplayName("用户不存在 → 报错")
    void setStatus_missing_rejected() {
        when(mallUserMapper.updateStatus(1L, 0, 9L)).thenReturn(0);
        assertThrows(BaseException.class, () -> service.setStatus(1L, 0, 9L));
    }

    @Test
    @DisplayName("锁定用户 → 更新成功")
    void setStatus_success() {
        when(mallUserMapper.updateStatus(1L, 0, 9L)).thenReturn(1);
        service.setStatus(1L, 0, 9L);
        verify(mallUserMapper).updateStatus(1L, 0, 9L);
    }
}