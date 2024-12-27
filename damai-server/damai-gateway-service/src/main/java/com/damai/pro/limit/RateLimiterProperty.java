package com.damai.pro.limit;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;

/**
 * 线上限流工具属性
 * 用于配置线上环境的限流参数，通过Spring的@Value注解从配置文件中获取配置
 **/
@Data
public class RateLimiterProperty {

	// 限流开关，默认为false，表示不限流
	@Value("${rate.switch:false}")
	private Boolean rateSwitch;

	// 最大并发许可数，默认为200
	@Value("${rate.permits:200}")
	private Integer ratePermits;
}
