package com.damai.exception;

import lombok.Data;

/**
 * 该类用于表示参数错误的信息
 * 它包含了参数名和错误消息，用于在接口响应中指出参数错误的详细信息
 **/
@Data
public class ArgumentError {

	/**
	 * 参数名，用于标识发生错误的参数
	 **/
	private String argumentName;

	/**
	 * 错误消息，描述参数错误的详细信息
	 **/
	private String message;
}
