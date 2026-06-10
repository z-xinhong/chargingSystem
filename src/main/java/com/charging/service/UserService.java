package com.charging.service;

import com.charging.common.Result;
import com.charging.dto.LoginDTO;
import com.charging.dto.RegisterDTO;
import com.charging.dto.UserProfileDTO;

public interface UserService {
    Result register(RegisterDTO dto);

    Result login(LoginDTO dto);

    Result profile(Long userId);

    Result updateProfile(Long userId, UserProfileDTO dto);
}
