package com.damai.conf;

import com.damai.common.ApiResponse;
import lombok.Data;

import java.util.Map;

/**
 * 请求临时包装类
 * 用于临时存储请求相关的数据，方便进行统一的处理和响应
 * 主要包装了两部分数据：map用于存储请求参数或其他临时数据，
 * apiResponse用于存储对请求的响应数据
 */
@Data
public class RequestTemporaryWrapper {

	/**
	 * 存储请求参数或其他临时数据
	 * 使用Map结构以便于快速的插入和查询数据
	 */
	private Map<String, String> map;

	/**
	 * 存储对请求的响应数据
	 * 使用ApiResponse对象以便于统一响应格式和处理逻辑
	 */
	private ApiResponse<?> apiResponse;
}
