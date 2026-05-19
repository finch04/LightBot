package com.lightbot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.lightbot.mapper")
@EnableAsync
public class LightBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(LightBotApplication.class, args);
        System.out.println("LightBot started successfully.");
    }
}
