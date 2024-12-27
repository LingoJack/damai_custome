package com.damai.core;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import static com.damai.constant.Constant.DEFAULT_PREFIX_DISTINCTION_NAME;
import static com.damai.constant.Constant.PREFIX_DISTINCTION_NAME;

/**
 * SpringUtil类实现了ApplicationContextInitializer接口，用于初始化Spring应用上下文
 * 它提供了一种获取特定属性值的方法，并在应用启动时初始化上下文
 */
public class SpringUtil implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	/**
	 * 定义一个静态变量以存储ConfigurableApplicationContext实例，便于后续访问
	 */
	private static ConfigurableApplicationContext configurableApplicationContext;

	/**
	 * 获取前缀区分名称
	 * 这个方法从应用的环境属性中获取名为PREFIX_DISTINCTION_NAME的属性值
	 * 如果该属性未设置，则返回默认值DEFAULT_PREFIX_DISTINCTION_NAME
	 *
	 * @return 前缀区分名称，如果未设置则返回默认值
	 */
	public static String getPrefixDistinctionName() {
		// key 缺省值
		return configurableApplicationContext
				.getEnvironment()
				.getProperty(PREFIX_DISTINCTION_NAME, DEFAULT_PREFIX_DISTINCTION_NAME);
	}

	/**
	 * 初始化应用的ConfigurableApplicationContext
	 * 当应用启动时，Spring框架会调用这个方法来设置应用上下文
	 *
	 * @param applicationContext 应用的ConfigurableApplicationContext实例
	 */
	@Override
	public void initialize(final ConfigurableApplicationContext applicationContext) {
		configurableApplicationContext = applicationContext;
	}
}

