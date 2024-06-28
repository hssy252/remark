package com.remark;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author 13759
 */
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.remark.mapper")
@SpringBootApplication
public class RemarkApplication {

    public static void main(String[] args) {
        SpringApplication.run(RemarkApplication.class, args);
    }

}
