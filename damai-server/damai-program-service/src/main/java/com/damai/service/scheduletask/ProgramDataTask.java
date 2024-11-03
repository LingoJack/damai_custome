package com.damai.service.scheduletask;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.BusinessThreadPool;
import com.damai.dto.ProgramResetExecuteDto;
import com.damai.service.ProgramService;
import com.damai.service.init.ProgramElasticsearchInitData;
import com.damai.service.init.ProgramShowTimeRenewal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @program: damai
 * @description: 节目数据定时任务类，负责每日定时重置和更新节目相关数据
 * @author: k
 * @create: 2024-06-05
 **/
@Slf4j
@Component
public class ProgramDataTask {

    // 注入Spring应用上下文，用于初始化节目数据
    @Autowired
    private ConfigurableApplicationContext applicationContext;

    // 注入节目服务，用于获取节目列表和重置节目执行状态
    @Autowired
    private ProgramService programService;

    // 注入节目演出时间更新服务，用于每日更新节目演出时间
    @Autowired
    private ProgramShowTimeRenewal programShowTimeRenewal;

    // 注入节目Elasticsearch初始化数据服务，用于每日重新初始化节目Elasticsearch数据
    @Autowired
    private ProgramElasticsearchInitData programElasticsearchInitData;

    /**
     * 定时任务方法，用于每日重置和更新节目数据
     * 该方法在每日23:00:00执行
     */
    @Scheduled(cron = "0 0 23 * * ?")
    public void executeTask() {
        // 使用业务线程池执行定时任务，避免阻塞主线程
        BusinessThreadPool.execute(() -> {
            try {
                // 日志记录定时任务开始执行
                log.warn("定时任务重置执行");
                // 获取所有节目的ID列表
                List<Long> allProgramIdList = programService.getAllProgramIdList();
                // 检查节目ID列表是否非空
                if (CollectionUtil.isNotEmpty(allProgramIdList)) {
                    // 遍历每个节目ID，重置每个节目的执行状态
                    for (Long programId : allProgramIdList) {
                        ProgramResetExecuteDto programResetExecuteDto = new ProgramResetExecuteDto();
                        programResetExecuteDto.setProgramId(programId);
                        programService.resetExecute(programResetExecuteDto);
                    }
                }
                // 执行节目演出时间更新初始化操作
                programShowTimeRenewal.executeInit(applicationContext);
                // 执行节目Elasticsearch数据初始化操作
                programElasticsearchInitData.executeInit(applicationContext);

            }
            catch (Exception e) {
                // 日志记录定时任务执行错误
                log.error("executeTask error", e);
            }
        });
    }
}

