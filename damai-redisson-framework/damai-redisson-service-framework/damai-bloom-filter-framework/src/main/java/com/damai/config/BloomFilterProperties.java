package com.damai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = BloomFilterProperties.PREFIX)
public class BloomFilterProperties {

	public static final String PREFIX = "bloom-filter";

	/**
	 * 布隆过滤器名字
	 */
	private String name;

	/**
	 * 布隆过滤器的容量
	 */
	private Long expectedInsertions = 20000L;

	/**
	 * 布隆过滤器碰撞率
	 */
	private Double falseProbability = 0.01D;
}
