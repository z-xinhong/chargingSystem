package com.charging.controller;

import com.charging.common.Result;
import com.charging.service.SimulatedClockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/time")
public class TimeController {

    @Autowired
    private SimulatedClockService simulatedClockService;

    @GetMapping("/current")
    public Result current() {
        return Result.success(simulatedClockService.snapshot());
    }
}
