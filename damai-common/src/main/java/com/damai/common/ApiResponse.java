package com.damai.common;

import com.damai.enums.BaseCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * ApiResponse类用于定义API响应的通用结构
 * 它实现了Serializable接口，以便于对象的序列化和反序列化
 * 使用Lombok的@Data注解，自动生成getter和setter方法，简化代码
 *
 * @param <T> 泛型参数，表示响应数据的具体类型
 */
@Data
@Schema(title = "ApiResponse", description = "数据响应规范结构")
public class ApiResponse<T> implements Serializable {

    /**
     * 响应码，用于表示响应的状态 0:成功 其余:失败
     */
    @Schema(name = "code", type = "Integer", description = "响应码 0:成功 其余:失败")
    private Integer code;

    /**
     * 错误信息，当响应码非0时，提供错误的详细信息
     */
    @Schema(name = "message", type = "String", description = "错误信息")
    private String message;

    /**
     * 响应的具体数据，类型由调用时指定的泛型决定
     */
    @Schema(name = "data", description = "响应的具体数据")
    private T data;

    // 私有构造器，防止外部直接实例化
    private ApiResponse() {
    }

    /**
     * 创建一个错误响应实例
     *
     * @param code    错误码
     * @param message 错误信息
     * @return 错误响应实例
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = code;
        apiResponse.message = message;
        return apiResponse;
    }

    /**
     * 创建一个带有默认错误码的错误响应实例
     *
     * @param message 错误信息
     * @return 错误响应实例
     */
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = -100;
        apiResponse.message = message;
        return apiResponse;
    }

    /**
     * 创建一个带有错误码和数据的错误响应实例
     *
     * @param code 错误码
     * @param data 响应数据
     * @return 错误响应实例
     */
    public static <T> ApiResponse<T> error(Integer code, T data) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = -100;
        apiResponse.data = data;
        return apiResponse;
    }

    /**
     * 根据BaseCode枚举创建一个错误响应实例
     *
     * @param baseCode 包含错误码和信息的枚举实例
     * @return 错误响应实例
     */
    public static <T> ApiResponse<T> error(BaseCode baseCode) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = baseCode.getCode();
        apiResponse.message = baseCode.getMsg();
        return apiResponse;
    }

    /**
     * 根据BaseCode枚举和具体数据创建一个错误响应实例
     *
     * @param baseCode 包含错误码和信息的枚举实例
     * @param data     响应数据
     * @return 错误响应实例
     */
    public static <T> ApiResponse<T> error(BaseCode baseCode, T data) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = baseCode.getCode();
        apiResponse.message = baseCode.getMsg();
        apiResponse.data = data;
        return apiResponse;
    }

    /**
     * 创建一个带有默认错误码和信息的错误响应实例
     *
     * @return 错误响应实例
     */
    public static <T> ApiResponse<T> error() {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = -100;
        apiResponse.message = "系统错误，请稍后重试!";
        return apiResponse;
    }

    /**
     * 创建一个成功的响应实例，不包含任何数据
     *
     * @return 成功响应实例
     */
    public static <T> ApiResponse<T> ok() {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = 0;
        return apiResponse;
    }

    /**
     * 创建一个包含具体数据的成功响应实例
     *
     * @param t 响应的数据
     * @return 成功响应实例
     */
    public static <T> ApiResponse<T> ok(T t) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = 0;
        apiResponse.setData(t);
        return apiResponse;
    }
}
