package com.charging.controller;

import com.charging.common.Result;
import com.charging.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    @GetMapping("/system")
    public Result getSystemConfig() {
        return Result.success(systemConfigService.getConfig());
    }

    @PostMapping("/system")
    public Result saveSystemConfig(@RequestBody(required = false) Map<String, Object> config) {
        return Result.success(systemConfigService.saveConfig(config));
    }
}
