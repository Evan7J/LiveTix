package com.livetix.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${livetix.upload.dir}")
    private String uploadDir;

    @Value("${livetix.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 解析为绝对路径并确保目录存在
        File dir = Paths.get(uploadDir).toAbsolutePath().normalize().toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String location = "file:" + dir.getAbsolutePath() + "/";
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(location);
    }
}
