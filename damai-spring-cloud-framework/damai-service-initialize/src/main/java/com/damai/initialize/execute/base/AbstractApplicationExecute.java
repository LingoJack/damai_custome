package com.damai.initialize.execute.base;

import com.damai.initialize.base.InitializeHandler;
import lombok.AllArgsConstructor;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Comparator;
import java.util.Map;

/**
 * 用于处理应用程序启动执行的基类
 * 它提供了一种机制来执行初始化处理，允许根据类型和执行顺序对初始化处理器进行排序和执行
 **/
@AllArgsConstructor
public abstract class AbstractApplicationExecute {

    // 应用程序上下文，用于获取初始化处理器实例
    private final ConfigurableApplicationContext applicationContext;

    /**
     * 执行初始化处理
     * 该方法从应用程序上下文中获取所有{@link InitializeHandler}类型的bean，
     * 过滤出与当前执行类型匹配的handler，
     * 并按照执行顺序（executeOrder）排序，然后依次调用它们的executeInit方法进行初始化
     */
    public void execute() {
        // 获取所有InitializeHandler类型的bean
        Map<String, InitializeHandler> initializeHandlerMap = applicationContext.getBeansOfType(InitializeHandler.class);
        // 过滤、排序并执行初始化处理
        initializeHandlerMap.values()
                .stream()
                .filter(initializeHandler -> initializeHandler.type().equals(type()))
                .sorted(Comparator.comparingInt(InitializeHandler::executeOrder))
                .forEach(initializeHandler -> {
                    initializeHandler.executeInit(applicationContext);
                });
    }

    /**
     * 初始化执行 类型
     * 定义了当前执行环境的类型，子类需要实现该方法以提供具体的类型标识
     *
     * @return 类型
     */
    public abstract String type();
}
