package com.charging.controller;

import com.charging.common.Result;
import com.charging.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/list")
    public Result list(@RequestParam(required = false, defaultValue = "DAY") String period) {
        return reportService.list(period);
    }
}
