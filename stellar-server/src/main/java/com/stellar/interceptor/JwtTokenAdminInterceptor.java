package com.stellar.interceptor;

import com.stellar.constant.JwtClaimsConstant;
import com.stellar.properties.JwtProperties;
import com.stellar.service.TokenBlacklistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 管理端（Employee）JWT 拦截器：token header 为 {@code token}，claim 为 {@code EMP_ID}。
 * <p>校验逻辑与 C 端共用 {@link AbstractJwtTokenInterceptor}，仅密钥 / header / claim 不同。</p>
 */
@Component
public class JwtTokenAdminInterceptor extends AbstractJwtTokenInterceptor {

    @Autowired
    public JwtTokenAdminInterceptor(JwtProperties jwtProperties,
                                    TokenBlacklistService tokenBlacklistService) {
        super(jwtProperties, tokenBlacklistService);
    }

    @Override
    protected String tokenHeaderName() {
        return jwtProperties.getAdminTokenName();
    }

    @Override
    protected String secretKey() {
        return jwtProperties.getAdminSecretKey();
    }

    @Override
    protected String claimName() {
        return JwtClaimsConstant.EMP_ID;
    }
}
