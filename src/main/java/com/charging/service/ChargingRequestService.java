package com.charging.service;

import com.charging.common.Result;
import com.charging.dto.ModifyRequestDTO;
import com.charging.dto.RequestDTO;

public interface ChargingRequestService {
    Result submit(Long userId, RequestDTO dto);

    Result modify(Long userId, ModifyRequestDTO dto);

    Result cancel(Long userId, Long requestId);

    Result status(Long userId, Long requestId);
}
