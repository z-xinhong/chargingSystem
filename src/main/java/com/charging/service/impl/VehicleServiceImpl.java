package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charging.common.Result;
import com.charging.entity.ChargingRequest;
import com.charging.entity.User;
import com.charging.mapper.ChargingRequestMapper;
import com.charging.mapper.UserMapper;
import com.charging.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VehicleServiceImpl implements VehicleService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ChargingRequestMapper chargingRequestMapper;

    @Override
    public Result list(String keyword) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(item -> item.like("username", keyword)
                    .or().like("phone", keyword)
                    .or().like("plate_no", keyword));
        }
        wrapper.orderByAsc("id");

        List<Map<String, Object>> data = new ArrayList<>();
        for (User user : userMapper.selectList(wrapper)) {
            data.add(toVehicleResponse(user));
        }
        return Result.success(data);
    }

    @Override
    public Result save(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return Result.error("用户名不能为空");
        }

        if (user.getId() == null) {
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                user.setPassword("123456");
            }
            if (user.getRole() == null || user.getRole().isBlank()) {
                user.setRole("USER");
            }
            userMapper.insert(user);
        } else {
            User old = userMapper.selectById(user.getId());
            if (old == null) {
                return Result.error("用户不存在");
            }
            old.setUsername(user.getUsername());
            old.setPhone(user.getPhone());
            old.setPlateNo(user.getPlateNo());
            old.setBatteryCapacity(user.getBatteryCapacity());
            old.setRole(user.getRole() == null ? old.getRole() : user.getRole());
            userMapper.updateById(old);
            user = old;
        }

        return Result.success(toVehicleResponse(user));
    }

    @Override
    public Result delete(Long userId) {
        if (userId == null) {
            return Result.error("用户ID不能为空");
        }
        userMapper.deleteById(userId);
        return Result.success();
    }

    private Map<String, Object> toVehicleResponse(User user) {
        ChargingRequest request = currentRequest(user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("phone", user.getPhone());
        data.put("plateNo", user.getPlateNo());
        data.put("batteryCapacity", user.getBatteryCapacity());
        data.put("currentRequest", request == null ? null : request.getQueueNumber());
        data.put("status", request == null ? "IDLE" : request.getStatus());
        return data;
    }

    private ChargingRequest currentRequest(Long userId) {
        QueryWrapper<ChargingRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .notIn("status", "COMPLETED", "CANCELLED")
                .orderByDesc("created_at")
                .last("limit 1");
        return chargingRequestMapper.selectOne(wrapper);
    }
}
