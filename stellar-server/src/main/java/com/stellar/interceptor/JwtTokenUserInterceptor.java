package com.stellar.interceptor;

import com.stellar.constant.JwtClaimsConstant;
import com.stellar.properties.JwtProperties;
import com.stellar.service.TokenBlacklistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * C 端（用户 / 小程序）JWT 拦截器：token header 为 {@code authentication}，claim 为 {@code USER_ID}。
 * <p>校验逻辑与管理端共用 {@link AbstractJwtTokenInterceptor}，仅密钥 / header / claim 不同。</p>
 */
@Component
public class JwtTokenUserInterceptor extends AbstractJwtTokenInterceptor {

    @Autowired
    public JwtTokenUserInterceptor(JwtProperties jwtProperties,
                                   TokenBlacklistService tokenBlacklistService) {
        super(jwtProperties, tokenBlacklistService);
    }

    @Override
    protected String tokenHeaderName() {
        return jwtProperties.getUserTokenName();
    }

    @Override
    protected String secretKey() {
        return jwtProperties.getUserSecretKey();
    }

    @Override
    protected String claimName() {
        return JwtClaimsConstant.USER_ID;
    }
}
