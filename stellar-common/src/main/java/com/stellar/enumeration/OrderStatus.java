package com.stellar.enumeration;

import lombok.Getter;

/**
 * 订单状态枚举。
 * <p>统一后端数据库存储字符串与前端数字状态码，避免魔法数字/字符串散落。</p>
 */
@Getter
public enum OrderStatus {

    /** 已取消 */
    CANCELLED("CANCELLED", 0, "已取消"),
    /** 待付款 */
    PENDING("PENDING", 1, "待付款"),
    /** 待发货（已支付） */
    PAID("PAID", 2, "待发货"),
    /** 待收货（已发货） */
    SHIPPED("SHIPPED", 3, "待收货"),
    /** 已完成 */
    COMPLETED("COMPLETED", 5, "已完成"),
    /** 退款中 */
    REFUNDING("REFUNDING", 6, "退款中"),
    /** 已退款 */
    REFUNDED("REFUNDED", 7, "已退款");

    private final String backendValue;
    private final int frontendCode;
    private final String description;

    OrderStatus(String backendValue, int frontendCode, String description) {
        this.backendValue = backendValue;
        this.frontendCode = frontendCode;
        this.description = description;
    }

    /**
     * 根据后端字符串值查找枚举。
     */
    public static OrderStatus fromBackendValue(String value) {
        if (value == null) return null;
        for (OrderStatus s : values()) {
            if (s.backendValue.equals(value)) {
                return s;
            }
        }
        return null;
    }

    /**
     * 根据前端数字状态码查找枚举。
     */
    public static OrderStatus fromFrontendCode(Integer code) {
        if (code == null) return null;
        for (OrderStatus s : values()) {
            if (s.frontendCode == code) {
                return s;
            }
        }
        return null;
    }
}
