package com.example.delayqueuenew.event;

import cn.hutool.core.collection.CollectionUtil;
import com.example.delayqueuenew.context.DelayQueueCombine;
import com.example.delayqueuenew.context.DelayQueueContext;
import com.example.delayqueuenew.context.DelayQueuePart;
import com.example.delayqueuenew.core.ConsumerTask;
import com.example.redisson.RedissonProperties;
import lombok.AllArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;

import java.util.Map;

/**
 * DelayQueueInitHandler 类用于处理应用程序启动事件。
 */
@AllArgsConstructor
public class DelayQueueInitHandler implements ApplicationListener<ApplicationStartedEvent> {
    
    private final RedissonProperties redissonProperties;
    
    private final RedissonClient redissonClient;
    
    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        Map<String, ConsumerTask> consumerTaskMap = event.getApplicationContext().getBeansOfType(ConsumerTask.class);
        if (CollectionUtil.isEmpty(consumerTaskMap)) {
            return;
        }
        for (ConsumerTask consumerTask : consumerTaskMap.values()) {
            DelayQueuePart delayQueuePart = new DelayQueuePart(redissonClient, redissonProperties.getThreadCount(), redissonProperties.getIsolationRegionCount(), consumerTask);
            DelayQueueCombine delayQueueCombine = new DelayQueueCombine(delayQueuePart);
            DelayQueueContext.put(consumerTask.topic(),delayQueueCombine);
        }
    }
    
    
}