package com.charging.dto;

import lombok.Data;

@Data
public class RequestDTO {
    private String mode;
    private Double requestedKwh;
}
