package com.charging.dto;

import lombok.Data;

@Data
public class RequestDTO {
    private Long chargerId;
    private Double requiredEnergy;
    private String startTime;
    private String endTime;
}