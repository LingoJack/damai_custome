package com.damai.initialize.base;

import org.springframework.beans.factory.InitializingBean;

import static com.damai.initialize.constant.InitializeHandlerType.APPLICATION_EVENT_LISTENER;

/**
 * 用于处理 {@link InitializingBean} 类型 初始化执行 抽象
 * 提供了类型方法的实现，子类只需要实现具体的初始化逻辑即可
 **/
public abstract class AbstractApplicationStartEventListenerHandler implements InitializeHandler {

	/**
	 * 返回处理程序的类型，表示它处理的是 APPLICATION_EVENT_LISTENER 类型的事件
	 *
	 * @return 类型标识符，表示处理程序的类型
	 */
	@Override
	public String type() {
		return APPLICATION_EVENT_LISTENER;
	}
}
