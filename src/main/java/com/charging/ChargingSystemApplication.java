package com.charging;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.charging.mapper")
@SpringBootApplication
public class ChargingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                ChargingSystemApplication.class,
                args
        );
    }
}
