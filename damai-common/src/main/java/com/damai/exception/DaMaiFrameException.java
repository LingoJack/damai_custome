package com.damai.exception;

import com.damai.common.ApiResponse;
import com.damai.enums.BaseCode;
import lombok.Data;

/**
 * 大麦框架异常类，继承自BaseException
 * 提供了多个构造方法以适应不同的异常处理场景
 */
@Data
public class DaMaiFrameException extends BaseException {

    /**
     * 错误代码
     */
    private Integer code;

    /**
     * 错误消息
     */
    private String message;

    /**
     * 默认构造方法
     */
    public DaMaiFrameException() {
        super();
    }

    /**
     * 构造方法，只传入错误消息
     *
     * @param message 错误消息
     */
    public DaMaiFrameException(String message) {
        super(message);
    }

    /**
     * 构造方法，传入错误代码和消息
     * 将字符串代码转换为Integer类型
     *
     * @param code    错误代码
     * @param message 错误消息
     */
    public DaMaiFrameException(String code, String message) {
        super(message);
        this.code = Integer.parseInt(code);
        this.message = message;
    }

    /**
     * 构造方法，传入错误代码和消息
     *
     * @param code    错误代码
     * @param message 错误消息
     */
    public DaMaiFrameException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    /**
     * 构造方法，传入BaseCode枚举对象
     *
     * @param baseCode 包含错误代码和消息的枚举对象
     */
    public DaMaiFrameException(BaseCode baseCode) {
        super(baseCode.getMsg());
        this.code = baseCode.getCode();
        this.message = baseCode.getMsg();
    }

    /**
     * 构造方法，传入ApiResponse对象
     *
     * @param apiResponse 包含错误代码和消息的API响应对象
     */
    public DaMaiFrameException(ApiResponse apiResponse) {
        super(apiResponse.getMessage());
        this.code = apiResponse.getCode();
        this.message = apiResponse.getMessage();
    }

    /**
     * 构造方法，只传入异常原因
     *
     * @param cause 异常原因
     */
    public DaMaiFrameException(Throwable cause) {
        super(cause);
    }

    /**
     * 构造方法，传入错误消息和异常原因
     *
     * @param message 错误消息
     * @param cause   异常原因
     */
    public DaMaiFrameException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    /**
     * 构造方法，传入错误代码、消息和异常原因
     *
     * @param code    错误代码
     * @param message 错误消息
     * @param cause   异常原因
     */
    public DaMaiFrameException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }
}

