package com.charging.entity;

@Data
@TableName("user")
public class User {
    private Long id;
    private String username;
    private String password;
    private String phone;
    private Double batteryCapacity;
    private String role;
}