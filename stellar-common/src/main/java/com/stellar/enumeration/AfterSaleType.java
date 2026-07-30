package com.stellar.enumeration;

import lombok.Getter;

/**
 * 售后类型枚举。
 */
@Getter
public enum AfterSaleType {

    /** 仅退款 */
    REFUND_ONLY(1, "仅退款"),
    /** 退货退款 */
    RETURN_REFUND(2, "退货退款"),
    /** 换货 */
    EXCHANGE(3, "换货");

    private final int code;
    private final String description;

    AfterSaleType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static AfterSaleType fromCode(Integer code) {
        if (code == null) return null;
        for (AfterSaleType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}
