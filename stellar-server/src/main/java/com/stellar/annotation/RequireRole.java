package com.stellar.annotation;

import java.lang.annotation.*;

/**
 * 权限注解：标注在 Controller 方法上，限定只有特定角色可访问。
 * value 默认 1=超级管理员
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    /** 允许的角色：1=超级管理员 2=运营 3=客服 4=财务 */
    int[] value() default {1};
}