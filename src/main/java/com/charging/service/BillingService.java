package com.charging.service;

import com.charging.common.Result;
import com.charging.dto.EndChargingDTO;

public interface BillingService {
    Result endCharging(EndChargingDTO dto, Long userId);

    Result list(Integer page, Integer size, Long userId);

    Result detail(Long billId, Long userId);
}
