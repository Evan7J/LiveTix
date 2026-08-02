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
 * SpringDoc OpenAPI 配置，Swagger UI 接口文档
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
                        .description("LiveTix 演唱会票务系统 API 文档，包含公开接口、用户接口和管理员接口")
                        .contact(new Contact().name("LiveTix Team").email("admin@livetix.com"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("本地开发")
                ));
    }
}
