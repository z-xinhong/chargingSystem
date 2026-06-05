package com.charging.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("charging_request")
public class ChargingRequest {
    private Long id;
    private Long userId;
    private String mode; // FAST / SLOW
    private Double requestedKwh;
    private String queueNumber;
    private String queueType;
    private String status;
    private LocalDateTime createdAt;
}
