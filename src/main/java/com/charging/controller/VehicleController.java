package com.charging.controller;

import com.charging.common.Result;
import com.charging.entity.User;
import com.charging.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicle")
public class VehicleController {

    @Autowired
    private VehicleService vehicleService;

    @GetMapping("/list")
    public Result list(@RequestParam(required = false, defaultValue = "") String keyword) {
        return vehicleService.list(keyword);
    }

    @PostMapping("/save")
    public Result save(@RequestBody User user) {
        return vehicleService.save(user);
    }

    @DeleteMapping("/delete")
    public Result delete(@RequestParam Long userId) {
        return vehicleService.delete(userId);
    }
}
