package com.charging.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("charging_pile")
public class ChargingPile {
    private Long id;
    private String pileCode;
    private String type;
    private Double power;
    private String status;
    private Integer totalChargeCount;
    private Double totalChargeTime;
    private Double totalChargeKwh;
}
