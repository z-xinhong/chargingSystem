package com.charging.controller;

import com.charging.common.Result;
import com.charging.dto.EndChargingDTO;
import com.charging.service.BillingService;
import com.charging.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BillingController {

    @Autowired
    private BillingService billingService;

    @PostMapping("/charging/end")
    public Result endCharging(@RequestBody EndChargingDTO dto,
                              @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return billingService.endCharging(dto, userId);
    }

    @GetMapping("/bill/list")
    public Result billList(@RequestParam(required = false, defaultValue = "1") Integer page,
                           @RequestParam(required = false, defaultValue = "10") Integer size,
                           @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return billingService.list(page, size, userId);
    }

    @GetMapping("/bill/detail")
    public Result billDetail(@RequestParam Long billId,
                             @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return billingService.detail(billId, userId);
    }

    private Long getUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return JwtUtil.getUserIdFromToken(token);
    }
}
