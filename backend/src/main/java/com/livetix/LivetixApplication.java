package com.livetix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class LivetixApplication {

    public static void main(String[] args) {
        SpringApplication.run(LivetixApplication.class, args);
        System.out.println("""

                ============================================
                   LiveTix 在线票务系统启动成功!
                   API: http://localhost:8080
                   Swagger: http://localhost:8080/doc.html
                ============================================
                """);
    }
}
