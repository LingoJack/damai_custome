package com.damai.service.init;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.handler.BloomFilterHandler;
import com.damai.initialize.base.AbstractApplicationPostConstructHandler;
import com.damai.service.ProgramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 程序布隆过滤器初始化类
 * 继承自AbstractApplicationPostConstructHandler，用于在应用启动后初始化布隆过滤器
 */
@Component
public class ProgramBloomFilterInit extends AbstractApplicationPostConstructHandler {

    /**
     * 注入节目服务，用于获取所有的节目ID
     */
    @Autowired
    private ProgramService programService;

    /**
     * 注入布隆过滤器处理器，用于添加节目ID到布隆过滤器
     */
    @Autowired
    private BloomFilterHandler bloomFilterHandler;

    /**
     * 定义执行顺序
     *
     * @return 返回执行顺序的整数值
     */
    @Override
    public Integer executeOrder() {
        return 4;
    }

    /**
     * 执行初始化方法
     * 获取所有的节目ID，并将它们添加到布隆过滤器中
     *
     * @param context Spring的可配置应用上下文
     */
    @Override
    public void executeInit(final ConfigurableApplicationContext context) {
        // 获取所有节目ID列表
        List<Long> allProgramIdList = programService.getAllProgramIdList();
        // 如果节目ID列表为空，则直接返回
        if (CollectionUtil.isEmpty(allProgramIdList)) {
            return;
        }
        // 遍历节目ID列表，将每个节目ID添加到布隆过滤器中
        allProgramIdList.forEach(programId -> bloomFilterHandler.add(String.valueOf(programId)));
    }
}
