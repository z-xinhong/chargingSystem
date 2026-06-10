package com.charging.controller;

import com.charging.common.Result;
import com.charging.dto.LoginDTO;
import com.charging.dto.RegisterDTO;
import com.charging.dto.UserProfileDTO;
import com.charging.service.UserService;
import com.charging.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result register(@RequestBody RegisterDTO dto) {
        return userService.register(dto);
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginDTO dto) {
        return userService.login(dto);
    }

    @GetMapping("/profile")
    public Result profile(@RequestHeader("Authorization") String token) {
        return userService.profile(parseUserId(token));
    }

    @PutMapping("/profile")
    public Result updateProfile(@RequestHeader("Authorization") String token,
                                @RequestBody UserProfileDTO dto) {
        return userService.updateProfile(parseUserId(token), dto);
    }

    private Long parseUserId(String token) {
        return JwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
    }
}
