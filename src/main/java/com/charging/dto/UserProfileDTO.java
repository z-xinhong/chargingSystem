package com.charging.dto;

import lombok.Data;

@Data
public class UserProfileDTO {
    private String phone;
    private String plateNo;
    private Double batteryCapacity;
}
