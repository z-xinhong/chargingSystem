package com.charging.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    private Long id;
    private String username;
    private String password;
    private String phone;
    private String plateNo;
    private Double batteryCapacity;
    private String role;
    private LocalDateTime createdAt;
}
