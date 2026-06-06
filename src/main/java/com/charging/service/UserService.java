package com.charging.service;

import com.charging.common.Result;
import com.charging.dto.LoginDTO;
import com.charging.dto.RegisterDTO;

public interface UserService {
    Result register(RegisterDTO dto);

    Result login(LoginDTO dto);
}
