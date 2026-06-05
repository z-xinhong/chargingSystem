package com.charging.dto;

import lombok.Data;

@Data
public class ModifyRequestDTO {
    private Long requestId;
    private String mode;
    private Double requestedKwh;
}