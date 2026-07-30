package com.stellar.enumeration;

/**
 * 数据库操作类型（给 @AutoFill 用）。
 */
public enum OperationType {
    /** INSERT：需要自动填充 create_* + update_* 共 4 字段 */
    INSERT,
    /** UPDATE：只需自动填充 update_* 2 字段 */
    UPDATE
}
