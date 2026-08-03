package com.stellar.config;

import com.stellar.interceptor.JwtTokenAdminInterceptor;
import com.stellar.interceptor.JwtTokenUserInterceptor;
import com.stellar.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.List;

/**
 * Web MVC 总配置（对齐 sky-take-out WebMvcConfiguration）：
 *   1. Knife4j / Swagger 3.0 文档生成（双分组：管理端 + C 端）
 *   2. CORS 跨域放行（供前端 dev-server 和 RAG 内部调用）
 *   3. 双 JWT 拦截器（管理端 /admin/**、C 端 /user/**）
 *   4. Jackson ObjectMapper 统一配置（LocalDateTime 格式 yyyy-MM-dd HH:mm:ss）
 */
@Slf4j
@Configuration
public class WebMvcConfiguration extends WebMvcConfigurationSupport {

    private final JwtTokenAdminInterceptor adminInterceptor;
    private final JwtTokenUserInterceptor userInterceptor;

    @Autowired
    public WebMvcConfiguration(JwtTokenAdminInterceptor adminInterceptor,
                               JwtTokenUserInterceptor userInterceptor) {
        this.adminInterceptor = adminInterceptor;
        this.userInterceptor = userInterceptor;
    }

    // ========= 1. Knife4j/Swagger Docket 双分组（仅 dev 环境启用） =========
    @Bean
    @Profile("dev")
    public Docket docketAdmin() {
        ApiInfo info = new ApiInfoBuilder()
                .title("星耀商城管理端 API 文档")
                .version("1.0")
                .description("stellar-mall 管理端所有接口（/admin/**）")
                .build();
        return new Docket(DocumentationType.SWAGGER_2)
                .groupName("管理端接口")
                .apiInfo(info)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.stellar.controller.admin"))
                .paths(PathSelectors.any())
                .build();
    }

    @Bean
    @Profile("dev")
    public Docket docketUser() {
        ApiInfo info = new ApiInfoBuilder()
                .title("星耀商城 C 端 API 文档")
                .version("1.0")
                .description("stellar-mall C 端（小程序/H5）所有接口（/user/**）")
                .build();
        return new Docket(DocumentationType.SWAGGER_2)
                .groupName("C端接口")
                .apiInfo(info)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.stellar.controller.user"))
                .paths(PathSelectors.any())
                .build();
    }

    // 让 Knife4j/doc.html 静态资源能被访问
    @Override
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
        // Knife4j >= 4.1 还需要这个
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/META-INF/resources/");
    }

    // ========= 2. CORS 跨域 =========
    @Override
    protected void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("token", "authentication", "Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }

    // ========= 3. 双 JWT 拦截器 =========
    @Override
    protected void addInterceptors(InterceptorRegistry registry) {
        // —— 管理端拦截 /admin/**，放行登录和健康检查、文档
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns(
                        "/admin/employee/login",
                        "/health",
                        "/doc.html",
                        "/v2/api-docs",
                        "/v3/api-docs",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/swagger-ui/**",
                        "/favicon.ico"
                ).order(10);

        // —— C 端拦截 /user/**，放行登录、商品详情/分页等公开接口
        registry.addInterceptor(userInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns(
                        "/user/user/login",
                        "/user/spu/page",
                        "/user/spu/**",
                        "/user/banner/list",
                        "/user/category/list",
                        "/user/home-module/list",
                        "/user/shop/status",
                        "/health",
                        "/doc.html",
                        "/v2/api-docs",
                        "/v3/api-docs",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/swagger-ui/**",
                        "/favicon.ico"
                ).order(20);
    }

    // ========= 4. Jackson ObjectMapper 统一配置 =========
    @Override
    protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(new JacksonObjectMapper());
        // 放在 converters 列表最前面，优先被选择（否则会被 Spring 默认 ObjectMapper 抢走）
        converters.add(0, converter);
    }
}
