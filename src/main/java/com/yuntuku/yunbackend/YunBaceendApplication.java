package com.yuntuku.yunbackend;

import org.mybatis.spring.annotation.MapperScan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@MapperScan("com.yuntuku.yunbackend.mapper")
public class YunBaceendApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunBaceendApplication.class, args);
    }

}
