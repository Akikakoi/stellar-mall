package com.stellar.aspect;

import com.stellar.annotation.RequireRole;
import com.stellar.context.BaseContext;
import com.stellar.entity.Employee;
import com.stellar.exception.BaseException;
import com.stellar.mapper.EmployeeMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RoleCheckAspect 单元测试。
 * <p>
 * 验证角色校验切面：BaseContext 取 empId → 查库拿 role → 与注解允许值比对。
 * 覆盖 4 个场景：角色匹配放行、角色不匹配拒绝、未登录拒绝、账号不存在拒绝。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("角色校验切面 — RoleCheckAspect")
class RoleCheckAspectTest {

    @Mock private EmployeeMapper employeeMapper;
    @Mock private ProceedingJoinPoint pjp;

    private RoleCheckAspect aspect;

    private static final Long EMP_ID = 1L;

    @AfterEach
    void tearDown() {
        BaseContext.remove();
    }

    private RequireRole annotationWith(int... roles) {
        return new RequireRole() {
            @Override public int[] value() { return roles; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return RequireRole.class; }
        };
    }

    private Employee employee(int role) {
        return Employee.builder().id(EMP_ID).role(role).build();
    }

    @Test @DisplayName("角色匹配（运营 role=2 访问 {1,2}）→ 放行")
    void roleAllowed_proceeds() throws Throwable {
        aspect = new RoleCheckAspect(employeeMapper);
        BaseContext.setCurrentId(EMP_ID);
        when(employeeMapper.getById(EMP_ID)).thenReturn(employee(2));
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.checkRole(pjp, annotationWith(1, 2));

        assertEquals("ok", result);
        verify(pjp, times(1)).proceed();
    }

    @Test @DisplayName("角色不匹配（客服 role=3 访问 {1,2}）→ 抛 BaseException 权限不足")
    void roleNotAllowed_throws() throws Throwable {
        aspect = new RoleCheckAspect(employeeMapper);
        BaseContext.setCurrentId(EMP_ID);
        when(employeeMapper.getById(EMP_ID)).thenReturn(employee(3));

        BaseException ex = assertThrows(BaseException.class,
                () -> aspect.checkRole(pjp, annotationWith(1, 2)));

        assertTrue(ex.getMessage().contains("权限不足"));
        verify(pjp, never()).proceed();
    }

    @Test @DisplayName("未登录（BaseContext 无 empId）→ 抛 BaseException 未登录")
    void notLoggedIn_throws() throws Throwable {
        aspect = new RoleCheckAspect(employeeMapper);
        BaseContext.remove();

        BaseException ex = assertThrows(BaseException.class,
                () -> aspect.checkRole(pjp, annotationWith(1)));

        assertTrue(ex.getMessage().contains("未登录"));
        verify(pjp, never()).proceed();
    }

    @Test @DisplayName("账号不存在（查库为 null）→ 抛 BaseException 账号不存在")
    void employeeNotFound_throws() throws Throwable {
        aspect = new RoleCheckAspect(employeeMapper);
        BaseContext.setCurrentId(EMP_ID);
        when(employeeMapper.getById(EMP_ID)).thenReturn(null);

        BaseException ex = assertThrows(BaseException.class,
                () -> aspect.checkRole(pjp, annotationWith(1)));

        assertTrue(ex.getMessage().contains("账号不存在"));
        verify(pjp, never()).proceed();
    }

    @Test @DisplayName("role 为 null（异常数据）→ 视为 0，不匹配任何角色")
    void nullRole_denied() throws Throwable {
        aspect = new RoleCheckAspect(employeeMapper);
        BaseContext.setCurrentId(EMP_ID);
        Employee emp = Employee.builder().id(EMP_ID).role(null).build();
        when(employeeMapper.getById(EMP_ID)).thenReturn(emp);

        BaseException ex = assertThrows(BaseException.class,
                () -> aspect.checkRole(pjp, annotationWith(1, 2, 3, 4)));

        assertTrue(ex.getMessage().contains("权限不足"));
        verify(pjp, never()).proceed();
    }
}
