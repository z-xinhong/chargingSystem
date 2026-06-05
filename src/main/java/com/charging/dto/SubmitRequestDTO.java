package com.charging.dto;

import lombok.Data;

@Data
public class SubmitRequestDTO {
    private String mode;  // FAST / SLOW
    private Double requestedKwh;
}