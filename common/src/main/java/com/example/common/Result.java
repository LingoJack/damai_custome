package com.example.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @program: toolkit
 * @description:
 * @author: lk
 * @create: 2023-04-27
 **/
@Data
public class Result<T> implements Serializable {
    
    private Integer code;

    private String message;

    private T data;
    
    private Result() {}
    
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<T>();
        result.code = code;
        result.message = message;
        return result;
    }

    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<T>();
        result.code = -100;
        result.message = message;
        return result;
    }

    public static <T> Result<T> error() {
        Result<T> result = new Result<T>();
        result.code = -100;
        result.message = "系统错误，请稍后重试!";
        return result;
    }
    
    public static <T> Result<T> success(T t) {
        Result<T> result = new Result<T>();
        result.code = 0;
        result.setData(t);
        return result;
    }
}