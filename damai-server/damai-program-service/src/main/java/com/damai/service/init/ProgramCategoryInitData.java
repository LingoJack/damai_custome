package com.damai.service.init;

import com.damai.BusinessThreadPool;
import com.damai.initialize.base.AbstractApplicationPostConstructHandler;
import com.damai.service.ProgramCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 节目类别初始化数据类
 * 负责在应用启动后执行节目类别的相关初始化操作
 */
@Component
public class ProgramCategoryInitData extends AbstractApplicationPostConstructHandler {

    /**
     * 节目类别服务接口的实例
     * 用于执行节目类别相关的业务逻辑
     */
    @Autowired
    private ProgramCategoryService programCategoryService;

    /**
     * 获取当前处理器的执行顺序
     *
     * @return 处理器的执行顺序，数字越小，优先级越高
     */
    @Override
    public Integer executeOrder() {
        // 设定当前处理器的执行顺序为1
        return 1;
    }

    /**
     * 执行节目类别相关的初始化操作
     *
     * @param context Spring应用上下文，可用于访问应用中的bean和其他资源
     */
    @Override
    public void executeInit(final ConfigurableApplicationContext context) {
        // 提交一个异步任务到业务线程池，执行节目类别数据的Redis初始化
        BusinessThreadPool.execute(() -> {
            programCategoryService.programCategoryRedisDataInit();
        });
    }
}
