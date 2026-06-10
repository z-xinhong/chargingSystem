package com.charging.controller;

import com.charging.common.Result;
import com.charging.dto.AdminBulkRequestDTO;
import com.charging.service.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule")
public class ScheduleController {

    @Autowired
    private ScheduleService scheduleService;

    @PostMapping("/dispatch")
    public Result dispatch(@RequestParam(required = false, defaultValue = "BATCH_SHORTEST") String policy) {
        return scheduleService.dispatch(policy);
    }

    @PostMapping("/mode")
    public Result selectMode(@RequestParam String mode) {
        return scheduleService.selectMode(mode);
    }

    @PostMapping("/bulk-requests")
    public Result bulkCreateRequests(@RequestBody AdminBulkRequestDTO dto) {
        return scheduleService.bulkCreateRequests(dto);
    }

    @GetMapping("/snapshot")
    public Result snapshot() {
        return scheduleService.snapshot();
    }

    @GetMapping("/waiting")
    public Result waitingQueue() {
        return scheduleService.waitingQueue();
    }

    @GetMapping("/pile-queue")
    public Result pileQueue() {
        return scheduleService.pileQueue();
    }
}
