package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charging.common.Result;
import com.charging.dto.LoginDTO;
import com.charging.dto.RegisterDTO;
import com.charging.entity.User;
import com.charging.mapper.UserMapper;
import com.charging.service.UserService;
import com.charging.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public Result register(RegisterDTO dto) {
        if (dto == null || isBlank(dto.getUsername()) || isBlank(dto.getPassword()) || dto.getBatteryCapacity() == null) {
            return Result.error("参数错误");
        }
        if (dto.getUsername().length() < 3 || dto.getUsername().length() > 20) {
            return Result.error("用户名长度需为3-20位");
        }
        if (dto.getPassword().length() < 6 || dto.getPassword().length() > 20) {
            return Result.error("密码长度需为6-20位");
        }
        if (dto.getBatteryCapacity() <= 0) {
            return Result.error("电池容量必须大于0");
        }

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", dto.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            return Result.error("用户名已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword());
        user.setPhone(dto.getPhone());
        user.setBatteryCapacity(dto.getBatteryCapacity());
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        return Result.success(data);
    }

    @Override
    public Result login(LoginDTO dto) {
        if (dto == null || isBlank(dto.getUsername()) || isBlank(dto.getPassword())) {
            return Result.error("参数错误");
        }

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", dto.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null || !user.getPassword().equals(dto.getPassword())) {
            return Result.error("用户名或密码错误");
        }

        String role = isBlank(user.getRole()) ? "USER" : user.getRole();
        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), role);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("token", token);
        data.put("role", role);
        return Result.success(data);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
