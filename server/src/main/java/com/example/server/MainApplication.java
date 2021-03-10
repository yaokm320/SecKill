package com.example.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@ImportResource(value = {"classpath:spring/spring-jdbc.xml"})   // 导入jdbc配置文件
//@ImportResource(value = "classpath:spring/spring-shiro.xml")  // 导入shiro配置文件
@MapperScan(basePackages = "com.example.model.mapper")
@EnableScheduling   // 启用计时器
@EnableAsync        // 启用异步执行
public class MainApplication extends SpringBootServletInitializer{

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(MainApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class,args);
    }

}