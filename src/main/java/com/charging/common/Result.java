package com.charging.common;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    // 单参数 error
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    // 双参数 error
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}