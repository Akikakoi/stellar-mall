package com.stellar.aspect;

import com.stellar.annotation.AutoFill;
import com.stellar.constant.AutoFillConstant;
import com.stellar.context.BaseContext;
import com.stellar.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 公共 4 字段自动注入切面（对齐 sky-take-out AutoFillAspect）。
 *
 * 拦截规则：
 *   - 扫描 com.stellar.mapper 包下所有方法（execution）
 *   - 并且方法上标了 @AutoFill(OperationType.INSERT/UPDATE)
 *   - 对方法的第一个参数（实体对象），通过反射调用 setter 写入：
 *       INSERT → createTime/createUser/updateTime/updateUser
 *       UPDATE → updateTime/updateUser
 */
@Slf4j
@Component
@Aspect
public class AutoFillAspect {

    @Pointcut("(execution(* com.stellar.mapper.*.*(..)) || execution(* com.stellar.ragsync.mapper.*.*(..))) && @annotation(com.stellar.annotation.AutoFill)")
    public void autoFillPointCut() {}

    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint jp) {
        // 1. 拿到 @AutoFill 的 value
        MethodSignature sig = (MethodSignature) jp.getSignature();
        AutoFill annotation = sig.getMethod().getAnnotation(AutoFill.class);
        OperationType type = annotation.value();

        // 2. 拿到第一个方法参数（待注入的实体）
        Object[] args = jp.getArgs();
        if (args == null || args.length == 0) return;
        Object entity = args[0];

        // 3. 准备公共值
        LocalDateTime now = LocalDateTime.now();
        Long currentUserId = BaseContext.getCurrentId();
        // 测试/后台任务未经过 JWT 拦截器时，currentUserId 可能为 null → 用 0 作为「系统/测试用户」兜底，
        // 避免数据库 NOT NULL 字段报错（数据库 seed 里也用 create_user=0 标记系统初始化记录）。
        if (currentUserId == null) {
            currentUserId = 0L;
        }

        try {
            Method setUpdateTime = entity.getClass().getDeclaredMethod(
                    AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
            Method setUpdateUser = entity.getClass().getDeclaredMethod(
                    AutoFillConstant.SET_UPDATE_USER, Long.class);
            setUpdateTime.invoke(entity, now);
            setUpdateUser.invoke(entity, currentUserId);

            if (type == OperationType.INSERT) {
                Method setCreateTime = entity.getClass().getDeclaredMethod(
                        AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                Method setCreateUser = entity.getClass().getDeclaredMethod(
                        AutoFillConstant.SET_CREATE_USER, Long.class);
                setCreateTime.invoke(entity, now);
                setCreateUser.invoke(entity, currentUserId);
            }
        } catch (Exception e) {
            // 反射失败一般是实体类缺 getter/setter 或者缺字段，打 warn 不抛出不影响主流程
            log.warn("[AutoFillAspect] 自动注入失败，entity={}, msg={}",
                    entity == null ? null : entity.getClass().getName(), e.getMessage());
        }
    }
}
