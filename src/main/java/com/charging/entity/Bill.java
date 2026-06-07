package com.charging.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("bill")
public class Bill {
    private Long id;
    private Long requestId;
    private Long pileId;
    private Double actualKwh;
    private Double durationHours;
    private BigDecimal electricityFee;
    private BigDecimal serviceFee;
    private BigDecimal totalFee;
    private LocalDateTime createdAt;
}
