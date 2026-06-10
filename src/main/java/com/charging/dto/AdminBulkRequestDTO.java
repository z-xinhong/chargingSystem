package com.charging.dto;

import lombok.Data;

@Data
public class AdminBulkRequestDTO {
    private String mode;
    private Double requestedKwh;
    private Integer count;
}
