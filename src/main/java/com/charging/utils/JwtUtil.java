package com.charging.utils;

public class JwtUtil {

    private JwtUtil() {
    }

    public static String generateToken(Long userId) {
        return String.valueOf(userId);
    }

    public static Long parseToken(String token) {
        return Long.valueOf(token);
    }
}
