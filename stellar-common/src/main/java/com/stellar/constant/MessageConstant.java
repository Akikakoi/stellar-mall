package com.stellar.constant;

/**
 * 通用业务消息常量（对齐 sky MessageConstant）。
 */
public class MessageConstant {
    public static final String ALREADY_EXISTS = "已存在";
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String ILLEGAL_PARAMETER = "参数非法";
    public static final String LOGIN_FAILED = "登录失败，用户名或密码错误";
    public static final String ACCOUNT_LOCKED = "账号已被锁定";
    /** 登录失败次数过多导致的临时锁定，{0}=剩余分钟数 */
    public static final String ACCOUNT_LOCKED_BY_ATTEMPTS = "登录失败次数过多，账号已被临时锁定，请 %d 分钟后重试";
    public static final String ACCOUNT_CLOSED = "账号已注销，无法登录";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String PASSWORD_ERROR = "密码错误";
    public static final String UNAUTHORIZED = "未登录或登录已过期";
    public static final String NO_PERMISSION = "无权限执行此操作";
    public static final String ORDER_NOT_FOUND = "订单不存在";
    public static final String ORDER_STATUS_ERROR = "订单状态不允许当前操作";
    public static final String SHOPPING_CART_IS_NULL = "购物车为空，无法下单";
    public static final String ADDRESS_BOOK_IS_NULL = "请先添加收货地址";
    public static final String STOCK_NOT_ENOUGH = "库存不足";
    public static final String UPLOAD_FAILED = "文件上传失败";

    // -------- E3 图形验证码 --------
    public static final String CAPTCHA_REQUIRED = "请先完成图形验证码校验";
    public static final String CAPTCHA_INVALID = "图形验证码错误或已失效";

    // -------- M1 商品域 --------
    public static final String CATEGORY_NOT_FOUND = "分类不存在";
    public static final String CATEGORY_NAME_ALREADY_EXISTS = "同父分类下已存在同名分类";
    public static final String CATEGORY_LEVEL_LIMIT_EXCEEDED = "分类层级超过限制（最多 2 级）";
    public static final String CATEGORY_HAS_CHILDREN = "该分类下还有子分类，无法删除";
    public static final String CATEGORY_HAS_LINKED_SPUS = "该分类下还有商品，无法删除";
    public static final String CATEGORY_PARENT_NOT_FOUND = "父分类不存在";
    public static final String CATEGORY_PARENT_MUST_BE_LEVEL1 = "子分类的父分类必须是 1 级";
    public static final String SPU_NOT_FOUND = "SPU 不存在";
    public static final String SKU_NOT_FOUND = "SKU 不存在";

    // -------- 售后域 --------
    public static final String AFTER_SALE_NOT_FOUND = "售后单不存在";
    public static final String AFTER_SALE_STATUS_ERROR = "售后单状态不允许当前操作";
    public static final String AFTER_SALE_ALREADY_EXISTS = "该订单商品已有进行中的售后单";
    public static final String AFTER_SALE_ORDER_NOT_PAID = "仅已支付订单可申请售后";
}
