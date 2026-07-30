package com.stellar.aspect;

import com.stellar.annotation.RequireRole;
import com.stellar.context.BaseContext;
import com.stellar.entity.Employee;
import com.stellar.exception.BaseException;
import com.stellar.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@RequiredArgsConstructor
public class RoleCheckAspect {

    private final EmployeeMapper employeeMapper;

    @Around("@annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint pjp, RequireRole requireRole) throws Throwable {
        Long empId = BaseContext.getCurrentId();
        if (empId == null) {
            throw new BaseException("未登录");
        }
        Employee emp = employeeMapper.getById(empId);
        if (emp == null) {
            throw new BaseException("账号不存在");
        }
        int role = emp.getRole() != null ? emp.getRole() : 0;
        int[] allowed = requireRole.value();
        boolean hasPermission = Arrays.stream(allowed).anyMatch(r -> r == role);
        if (!hasPermission) {
            throw new BaseException("权限不足");
        }
        return pjp.proceed();
    }
}