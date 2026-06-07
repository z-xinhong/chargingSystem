package com.charging.service;

import com.charging.common.Result;
import com.charging.entity.User;

public interface VehicleService {
    Result list(String keyword);

    Result save(User user);

    Result delete(Long userId);
}
