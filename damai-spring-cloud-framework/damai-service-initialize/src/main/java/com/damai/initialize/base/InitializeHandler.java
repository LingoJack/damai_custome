package com.damai.initialize.base;

import org.springframework.context.ConfigurableApplicationContext;

/**
 * 初始化处理器顶级接口，用于在应用启动过程中执行特定的初始化逻辑
 * 此接口在{@link com.damai.initialize.execute.base.AbstractApplicationExecute}中被使用
 */
public interface InitializeHandler {

	/**
	 * 获取初始化执行的类型
	 *
	 * @return 类型字符串，表示初始化的类别
	 */
	String type();

	/**
	 * 获取执行顺序
	 *
	 * @return 顺序整数，值越小表示执行顺序越靠前
	 */
	Integer executeOrder();

	/**
	 * 执行具体的初始化逻辑
	 *
	 * @param context 容器上下文，用于访问Spring容器中的Bean和其他资源
	 */
	void executeInit(ConfigurableApplicationContext context);

}
