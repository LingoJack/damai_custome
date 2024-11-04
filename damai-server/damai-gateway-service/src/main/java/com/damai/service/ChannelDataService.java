package com.damai.service;

import com.damai.client.BaseDataClient;
import com.damai.common.ApiResponse;
import com.damai.core.RedisKeyManage;
import com.damai.dto.GetChannelDataByCodeDto;
import com.damai.enums.BaseCode;
import com.damai.exception.ArgumentError;
import com.damai.exception.ArgumentException;
import com.damai.exception.DaMaiFrameException;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.util.StringUtil;
import com.damai.vo.GetChannelDataVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.damai.constant.GatewayConstant.CODE;

/**
 * 渠道数据服务
 */
@Slf4j
@Service
public class ChannelDataService {

    private final static String EXCEPTION_MESSAGE = "code参数为空";

    @Lazy
    @Autowired
    private BaseDataClient baseDataClient;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    /**
     * 检查code是否为空，为空则报参数异常
     *
     * @param code 渠道代码
     */
    public void checkCode(String code) {
        if (StringUtil.isEmpty(code)) {
            // 写入参数异常
            ArgumentError argumentError = new ArgumentError();
            argumentError.setArgumentName(CODE);
            argumentError.setMessage(EXCEPTION_MESSAGE);

            // 添加到参数错误列表
            List<ArgumentError> argumentErrorList = new ArrayList<>();
            argumentErrorList.add(argumentError);

            // 报参数错误
            throw new ArgumentException(BaseCode.ARGUMENT_EMPTY.getCode(), argumentErrorList);
        }
    }

    /**
     * 获取渠道数据
     *
     * @param code 渠道代码
     * @return
     */
    public GetChannelDataVo getChannelDataByCode(String code) {
        // 检查渠道代码是否为空
        checkCode(code);

        // 根据渠道代码从redis里面获取渠道数据
        GetChannelDataVo channelDataVo = getChannelDataByRedis(code);

        // 如果redis里面没有则从客户端获取
        if (Objects.isNull(channelDataVo)) {
            channelDataVo = getChannelDataByClient(code);
            setChannelDataRedis(code, channelDataVo);
        }
        // 返回渠道数据
        return channelDataVo;
    }

    /**
     * 从redis里面获取渠道数据
     *
     * @param code 渠道（客户端）代码
     * @return GetChannelDataVo 渠道数据对象，包含渠道相关信息
     */
    private GetChannelDataVo getChannelDataByRedis(String code) {
        return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.CHANNEL_DATA, code), GetChannelDataVo.class);
    }

    /**
     * 将渠道数据写入redis
     *
     * @param code             客户端代码，用于写入redis
     * @param getChannelDataVo 渠道数据对象，包含渠道相关信息
     */
    private void setChannelDataRedis(String code, GetChannelDataVo getChannelDataVo) {
        redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.CHANNEL_DATA, code), getChannelDataVo);
    }

    /**
     * 根据客户端代码获取渠道数据
     * 此方法构造了一个异步请求，通过客户端代码来获取渠道数据如果在指定时间内未收到响应或接收到错误响应，
     * 则抛出相应的自定义异常
     *
     * @param code 客户端代码，用于查询渠道数据
     * @return GetChannelDataVo 渠道数据对象，包含渠道相关信息
     * @throws DaMaiFrameException 当线程被中断、执行异常、请求超时或渠道数据不存在时抛出
     */
    private GetChannelDataVo getChannelDataByClient(String code) {
        // 创建请求数据传输对象，用于封装查询条件
        GetChannelDataByCodeDto getChannelDataByCodeDto = new GetChannelDataByCodeDto();
        getChannelDataByCodeDto.setCode(code);

        // 提交异步任务到线程池执行，并获取未来结果对象
        Future<ApiResponse<GetChannelDataVo>> future = threadPoolExecutor.submit(
                () -> baseDataClient.getByCode(getChannelDataByCodeDto)
        );

        try {
            // 尝试在10秒内获取异步响应结果
            ApiResponse<GetChannelDataVo> getChannelDataApiResponse = future.get(10, TimeUnit.SECONDS);
            // 检查响应状态码，如果成功则返回渠道数据
            if (Objects.equals(getChannelDataApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                return getChannelDataApiResponse.getData();
            }
        }
        catch (InterruptedException e) {
            // 处理线程中断异常
            log.error("baseDataClient getByCode Interrupted", e);
            throw new DaMaiFrameException(BaseCode.THREAD_INTERRUPTED);
        }
        catch (ExecutionException e) {
            // 处理执行异常
            log.error("baseDataClient getByCode execution exception", e);
            throw new DaMaiFrameException(BaseCode.SYSTEM_ERROR);
        }
        catch (TimeoutException e) {
            // 处理超时异常
            log.error("baseDataClient getByCode timeout exception", e);
            throw new DaMaiFrameException(BaseCode.EXECUTE_TIME_OUT);
        }

        // 如果上述异常均未捕获，则抛出渠道数据不存在的异常
        throw new DaMaiFrameException(BaseCode.CHANNEL_DATA_NOT_EXIST);
    }
}
