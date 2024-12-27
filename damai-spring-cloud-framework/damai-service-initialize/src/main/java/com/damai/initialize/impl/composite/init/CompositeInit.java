package com.damai.initialize.impl.composite.init;

import com.damai.initialize.base.AbstractApplicationStartEventListenerHandler;
import com.damai.initialize.impl.composite.CompositeContainer;
import lombok.AllArgsConstructor;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * CompositeInit类：负责应用启动过程中的组合初始化任务。
 * 该类继承自AbstractApplicationStartEventListenerHandler，作为应用启动事件的监听器。
 * 它的核心作用是使用CompositeContainer来完成初始化工作。
 */
@AllArgsConstructor
public class CompositeInit extends AbstractApplicationStartEventListenerHandler {

	// compositeContainer是CompositeInit的核心依赖，用于执行具体的初始化任务。
	private final CompositeContainer compositeContainer;

	/**
	 * 确定执行顺序。
	 *
	 * @return 返回执行顺序的数值，数值越小越早执行。
	 */
	@Override
	public Integer executeOrder() {
		// 返回1，表示该初始化器的执行顺序为第一优先级。
		return 1;
	}

	/**
	 * 执行初始化操作。
	 *
	 * @param context Spring的配置上下文，提供对Spring应用程序资源的访问。
	 *                通过它，可以在初始化过程中获取或操作应用程序上下文中的各种资源。
	 */
	@Override
	public void executeInit(ConfigurableApplicationContext context) {
		// 调用compositeContainer的init方法，执行初始化任务。
		compositeContainer.init(context);
	}
}
