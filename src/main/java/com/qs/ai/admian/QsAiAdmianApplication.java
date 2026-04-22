package com.qs.ai.admian;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动类。
 *
 * @author codex
 */
@SpringBootApplication
@MapperScan("com.qs.ai.admian.mapper")
public class QsAiAdmianApplication {

    public static void main(String[] args) {
        SpringApplication.run(QsAiAdmianApplication.class, args);
    }
}
