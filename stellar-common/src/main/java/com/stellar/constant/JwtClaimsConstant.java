package com.stellar.constant;

/**
 * JWT Claims 常量（⚠️ 命名必须与 RAG Python 端约定完全一致——大小写敏感！）
 * <p>
 * 变更任何字段名时，务必同步修改：
 *   - RAG 端 backend/app/core/security.py 的「三段式 decode_token」逻辑
 *   - RAG 端 backend/app/dependencies.py 的 get_current_user() 提取 EMP_ID/USER_ID
 *   - 两端 .env 的 SECRET_KEY / STELLAR_ADMIN_SECRET_KEY / STELLAR_USER_SECRET_KEY
 */
public class JwtClaimsConstant {
    /** 管理端员工 ID（Long） */
    public static final String EMP_ID = "EMP_ID";
    /** C 端用户 ID（Long） */
    public static final String USER_ID = "USER_ID";
    /** 角色：admin / operator / customer-service / finance / user（String） */
    public static final String ROLE = "ROLE";
    /** 员工姓名 / 用户昵称（String） */
    public static final String NAME = "NAME";
    /** 昵称（可选 String） */
    public static final String NICKNAME = "NICKNAME";
}
