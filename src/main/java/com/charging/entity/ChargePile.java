package com.charging.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("charge_pile")
public class ChargePile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String mode;  // FAST / SLOW
    private String status;  // IDLE / CHARGING / FAULT / OFFLINE
    private Double power;  // 功率 30或10
    private Long currentUserId;
    private Long currentRequestId;
}