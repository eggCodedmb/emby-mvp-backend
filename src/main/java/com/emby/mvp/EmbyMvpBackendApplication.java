package com.emby.mvp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.emby.mvp.mapper")
public class EmbyMvpBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmbyMvpBackendApplication.class, args);
    }
}
