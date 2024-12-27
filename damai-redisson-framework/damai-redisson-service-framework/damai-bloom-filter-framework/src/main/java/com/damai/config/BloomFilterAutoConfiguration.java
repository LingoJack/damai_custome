package com.damai.config;

import com.damai.handler.BloomFilterHandler;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 自动装配类
 * 这是一个自定义的Spring Boot自动配置类，用于自动装配布隆过滤器相关组件。
 * 关于如何实现打包一个自己的maven依赖，可以参考以下步骤：
 * 1. 在项目的`META-INF/spring`目录下创建`org.springframework.boot.autoconfigure.AutoConfiguration.imports`文件。
 * 2. 在该文件中书写需要自动装配的类的全限定名，例如：`com.example.BloomFilterAutoConfiguration`。
 */
@EnableConfigurationProperties(BloomFilterProperties.class)
public class BloomFilterAutoConfiguration {

	/**
	 * 创建并配置布隆过滤器处理器
	 * 此方法创建一个布隆过滤器处理器实例，并注入Redisson客户端和布隆过滤器属性。
	 *
	 * @param redissonClient        Redisson客户端，用于连接Redis
	 * @param bloomFilterProperties 布隆过滤器的配置属性
	 * @return 配置好的布隆过滤器处理器实例
	 */
	@Bean
	public BloomFilterHandler rBloomFilterUtil(RedissonClient redissonClient, BloomFilterProperties bloomFilterProperties) {
		return new BloomFilterHandler(redissonClient, bloomFilterProperties);
	}
}
