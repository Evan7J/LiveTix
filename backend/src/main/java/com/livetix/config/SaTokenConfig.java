package com.livetix.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 路由拦截配置，区分公开接口、用户接口和管理后台的访问权限。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {

                    // --- Public routes: no auth needed ---
                    SaRouter
                            .match("/api/public/**")
                            .stop();  // stop = no further checks

                                        SaRouter
                            .match("/api/admin/**")
                            .check(r -> StpUtil.checkRoleOr("admin", "operator", "finance", "cs"));

                    // --- Product routes: require login (write operations) ---
                    // 公开接口（list/detail）在 PublicController 中
                    SaRouter
                            .match("/api/product/**")
                            .check(r -> StpUtil.checkLogin());

                    // --- User routes: require login ---
                    SaRouter
                            .match("/api/user/**")
                            .check(r -> StpUtil.checkLogin());

                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/public/**",
                        "/doc.html",
                        "/v3/api-docs/**",
                        "/swagger-ui/**"
                );
                    }
}
