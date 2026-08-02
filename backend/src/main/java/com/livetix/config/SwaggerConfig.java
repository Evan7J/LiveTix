package com.livetix.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 1: SpringDoc OpenAPI 配置 — Swagger UI 接口文档
 * 访问地址: http://localhost:8080/swagger-ui.html
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI livetixOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LiveTix API — 演唱会票务秒杀系统")
                        .version("1.0.0")
                        .description("""
                                ## 模块
                                - **Public API**: 无需登录（演出列表/详情/搜索/注册/登录）
                                - **User API**: 需登录（下单/支付/退票/收藏/实名/钱包）
                                - **Admin API**: 需管理员角色（演出管理/订单管理/退款审核/财务）

                                ## 高并发特性
                                - Lua 原子令牌桶限流
                                - Redis 库存预热 + Lua 原子扣减
                                - RocketMQ 异步下单削峰
                                - Redis SET NX 防重锁
                                - DB 乐观锁最终防线
                                """)
                        .contact(new Contact().name("LiveTix Team").email("admin@livetix.com"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("本地开发")
                ));
    }
}
