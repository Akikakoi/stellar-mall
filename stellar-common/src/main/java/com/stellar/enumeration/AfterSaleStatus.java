package com.stellar.enumeration;

import lombok.Getter;

/**
 * 售后状态枚举。
 * <p>统一后端数据库存储 TINYINT 与前端展示文案，避免魔法数字散落。</p>
 */
@Getter
public enum AfterSaleStatus {

    /** 申请中 */
    APPLIED(1, "申请中"),
    /** 商家审核中 */
    AUDITING(2, "商家审核中"),
    /** 用户退货中（仅退货退款类型） */
    RETURNING(3, "用户退货中"),
    /** 退款中 */
    REFUNDING(4, "退款中"),
    /** 已完成 */
    COMPLETED(5, "已完成"),
    /** 已拒绝 */
    REJECTED(6, "已拒绝"),
    /** 已取消 */
    CANCELLED(7, "已取消");

    private final int code;
    private final String description;

    AfterSaleStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 根据数据库 int 值查找枚举。 */
    public static AfterSaleStatus fromCode(Integer code) {
        if (code == null) return null;
        for (AfterSaleStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
