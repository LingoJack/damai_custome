/**
 * @(#)ParameterException.java 2011-12-20 Copyright 2011 it.kedacom.com, Inc.
 * All rights reserved.
 */

package com.damai.exception;

import lombok.Data;

import java.util.List;

/**
 * 参数异常类，用于处理和管理应用程序中的参数错误
 * 继承自BaseException，以获取基础异常处理功能
 */
@Data
public class ArgumentException extends BaseException {

	/**
	 * 错误代码，用于标识特定的错误类型
	 */
	private Integer code;

	/**
	 * 参数错误列表，包含详细的参数错误信息
	 */
	private List<ArgumentError> argumentErrorList;

	/**
	 * 构造函数，用于创建带有错误代码和参数错误列表的ArgumentException对象
	 *
	 * @param code              错误代码
	 * @param argumentErrorList 参数错误列表
	 */
	public ArgumentException(Integer code, List<ArgumentError> argumentErrorList) {
		this.code = code;
		this.argumentErrorList = argumentErrorList;
	}

	/**
	 * 构造函数，用于创建带有指定错误消息的ArgumentException对象
	 *
	 * @param message 错误消息
	 */
	public ArgumentException(String message) {
		super(message);
	}

	/**
	 * 构造函数，用于创建带有错误代码和消息的ArgumentException对象
	 *
	 * @param code    错误代码
	 * @param message 错误消息
	 */
	public ArgumentException(Integer code, String message) {
		super(message);
		this.code = code;
	}

	/**
	 * 构造函数，用于创建带有根本原因的ArgumentException对象
	 *
	 * @param cause 根本原因
	 */
	public ArgumentException(Throwable cause) {
		super(cause);
	}

	/**
	 * 构造函数，用于创建带有错误消息和根本原因的ArgumentException对象
	 *
	 * @param message 错误消息
	 * @param cause   根本原因
	 */
	public ArgumentException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * 构造函数，用于创建带有错误代码、消息和根本原因的ArgumentException对象
	 *
	 * @param code    错误代码
	 * @param message 错误消息
	 * @param cause   根本原因
	 */
	public ArgumentException(Integer code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}
}
