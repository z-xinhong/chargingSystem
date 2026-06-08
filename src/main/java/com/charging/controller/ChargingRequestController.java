package com.charging.controller;

import com.charging.common.Result;
import com.charging.dto.ModifyRequestDTO;
import com.charging.dto.RequestDTO;
import com.charging.service.ChargingRequestService;
import com.charging.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/request")
public class ChargingRequestController {

    @Autowired
    private ChargingRequestService service;

    @PostMapping("/submit")
    public Result submit(@RequestBody RequestDTO dto,
                         @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return service.submit(userId, dto);
    }

    @PutMapping("/modify")
    public Result modify(@RequestBody ModifyRequestDTO dto,
                         @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return service.modify(userId, dto);
    }

    @DeleteMapping("/cancel")
    public Result cancel(@RequestParam Long requestId,
                         @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return service.cancel(userId, requestId);
    }

    @GetMapping("/status")
    public Result status(@RequestParam Long requestId,
                         @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return service.status(userId, requestId);
    }

    @GetMapping("/list")
    public Result list(@RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return service.listActive(userId);
    }

    private Long getUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return JwtUtil.getUserIdFromToken(token);
    }
}
