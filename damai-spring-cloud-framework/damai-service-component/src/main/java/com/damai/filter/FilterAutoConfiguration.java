package com.damai.filter;

import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * 过滤器配置
 **/
public class FilterAutoConfiguration {

	@Bean
	@Order(-10)
	public RequestWrapperFilter requestWrapperFilter() {
		return new RequestWrapperFilter();
	}

	@Bean
	@Order(1)
	public BaseParameterFilter baseParameterFilter() {
		return new BaseParameterFilter();
	}
}
