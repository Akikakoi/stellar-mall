package com.stellar.config;

import com.stellar.interceptor.JwtTokenAdminInterceptor;
import com.stellar.interceptor.JwtTokenUserInterceptor;
import com.stellar.interceptor.RateLimitInterceptor;
import com.stellar.json.JacksonObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springdoc.core.models.GroupedOpenApi;

import java.util.Arrays;
import java.util.List;

/**
 * Web MVC 总配置（Spring Boot 3.x + SpringDoc OpenAPI + Knife4j 4.x）：
 *   1. SpringDoc OpenAPI 文档生成（双分组：管理端 + C 端）
 *   2. CORS 跨域放行
 *   3. 双 JWT 拦截器（管理端 /admin/**、C 端 /user/**）
 *   4. Jackson ObjectMapper 统一配置
 */
@Slf4j
@Configuration
public class WebMvcConfiguration extends WebMvcConfigurationSupport {

    private final JwtTokenAdminInterceptor adminInterceptor;
    private final JwtTokenUserInterceptor userInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Value("${stellar.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Autowired
    public WebMvcConfiguration(JwtTokenAdminInterceptor adminInterceptor,
                               JwtTokenUserInterceptor userInterceptor,
                               RateLimitInterceptor rateLimitInterceptor) {
        this.adminInterceptor = adminInterceptor;
        this.userInterceptor = userInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    // ========= 1. SpringDoc OpenAPI（Knife4j 4.x，仅 dev 环境启用） =========

    @Bean
    @Profile("dev")
    public OpenAPI stellarMallOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("星耀商城 API 文档")
                        .version("1.0")
                        .description("stellar-mall 全部接口（/admin/** + /user/**）"));
    }

    @Bean
    @Profile("dev")
    public GroupedOpenApi adminGroup() {
        return GroupedOpenApi.builder()
                .group("管理端接口")
                .packagesToScan("com.stellar.controller.admin")
                .build();
    }

    @Bean
    @Profile("dev")
    public GroupedOpenApi userGroup() {
        return GroupedOpenApi.builder()
                .group("C端接口")
                .packagesToScan("com.stellar.controller.user")
                .build();
    }

    @Override
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/META-INF/resources/");
    }

    // ========= 2. CORS =========
    @Override
    protected void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (origins.length == 0) {
            log.warn("[CORS] No allowed origins configured. Production must set STELLAR_CORS_ALLOWED_ORIGINS.");
            return;
        }
        log.info("[CORS] Allowed origins: {}", String.join(", ", origins));
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("token", "authentication", "Content-Disposition")
                // 鉴权走自定义 header（token / Authorization），不使用 Cookie，
                // 因此不开 allowCredentials——避免 "*" 通配 + 凭证组合（反射任意来源带凭证）的安全风险
                .maxAge(3600);
    }

    // ========= 3. Interceptors =========
    @Override
    protected void addInterceptors(InterceptorRegistry registry) {
        // 幂等性由 IdempotentAspect（AOP @Around）实现，不再用拦截器
        // 限流拦截器
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/admin/login", "/user/login/**", "/user/user/login", "/user/user/register", "/admin/employee/refresh", "/user/user/refresh", "/captcha/**", "/doc.html", "/swagger-ui/**", "/v3/api-docs/**")
                .order(0);

        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns(
                        "/admin/employee/login",
                        "/admin/employee/refresh",
                        "/health",
                        "/doc.html",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/webjars/**",
                        "/favicon.ico"
                ).order(10);

        registry.addInterceptor(userInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns(
                        "/user/user/login",
                        "/user/user/email-login",
                        "/user/user/refresh",
                        "/user/email-code/**",
                        "/user/spu/page",
                        "/user/spu/**",
                        "/user/banner/list",
                        "/user/category/list",
                        "/user/home-module/list",
                        "/user/site-config/**",
                        "/user/shop/status",
                        "/user/behavior/**",
                        "/health",
                        "/doc.html",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/webjars/**",
                        "/favicon.ico"
                ).order(20);
    }

    // ========= 4. Jackson ObjectMapper =========
    @Override
    protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(new JacksonObjectMapper());
        converters.add(0, converter);
    }
}
