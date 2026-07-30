package com.stellar.annotation;

import com.stellar.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注某个 Mapper 方法需要被 AutoFillAspect 拦截，自动注入公共 4 字段：
 *   create_time / create_user / update_time / update_user
 * <p>
 * 与 sky-take-out @AutoFill 完全同构。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {
    /** 标识是 INSERT 还是 UPDATE，决定注入 4 字段还是只注入 update_* */
    OperationType value();
}
