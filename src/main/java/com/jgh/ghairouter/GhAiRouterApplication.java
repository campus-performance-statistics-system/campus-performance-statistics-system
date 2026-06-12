package com.jgh.ghairouter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAspectJAutoProxy
@EnableAsync
@MapperScan("com.jgh.ghairouter.mapper")
public class GhAiRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(GhAiRouterApplication.class, args);
    }

}
