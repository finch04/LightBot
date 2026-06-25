package com.lightbot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.lightbot.mapper")
@EnableAsync
@EnableScheduling
public class LightBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(LightBotApplication.class, args);
        System.out.println("LightBot 启动成功！");
        System.out.println("LightBot started successfully.");
    }
}
