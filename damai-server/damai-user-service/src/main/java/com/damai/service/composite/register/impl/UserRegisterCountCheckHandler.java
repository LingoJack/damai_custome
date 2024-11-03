package com.damai.service.composite.register.impl;

import com.damai.dto.UserRegisterDto;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.service.composite.register.AbstractUserRegisterCheckHandler;
import com.damai.service.tool.RequestCounter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户注册频率检查处理器
 * 继承自AbstractUserRegisterCheckHandler，用于检查用户注册的频率是否过快
 */
@Component
public class UserRegisterCountCheckHandler extends AbstractUserRegisterCheckHandler {

    /**
     * 请求计数器，用于统计和限制用户注册请求的频率
     */
    @Autowired
    private RequestCounter requestCounter;

    /**
     * 执行用户注册频率检查
     * 如果检查结果为真，则抛出用户注册频率过快的异常
     *
     * @param param 用户注册所需的信息
     */
    @Override
    protected void execute(final UserRegisterDto param) {
        boolean result = requestCounter.onRequest();
        if (result) {
            throw new DaMaiFrameException(BaseCode.USER_REGISTER_FREQUENCY);
        }
    }

    /**
     * 执行父级顺序
     * 返回值表示执行父级处理器的顺序
     *
     * @return 执行父级顺序的整数
     */
    @Override
    public Integer executeParentOrder() {
        return 1;
    }

    /**
     * 执行层级
     * 返回值表示当前处理器在执行链中的层级
     *
     * @return 执行层级的整数
     */
    @Override
    public Integer executeTier() {
        return 2;
    }

    /**
     * 执行顺序
     * 返回值表示当前处理器在同层级中的执行顺序
     *
     * @return 执行顺序的整数
     */
    @Override
    public Integer executeOrder() {
        return 1;
    }
}
