package com.damai.service.composite.register.impl;

import com.damai.dto.UserRegisterDto;
import com.damai.service.UserService;
import com.damai.service.composite.register.AbstractUserRegisterCheckHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户存在性检查处理器
 * 继承自AbstractUserRegisterCheckHandler，用于检查用户是否已存在
 * 主要功能是通过UserService检查给定手机号的用户是否存在
 */
@Component
public class UserExistCheckHandler extends AbstractUserRegisterCheckHandler {

	@Autowired
	private UserService userService;

	/**
	 * 执行用户存在性检查
	 *
	 * @param userRegisterDto 用户注册数据传输对象，包含用户信息如手机号
	 *                        通过userService的doExist方法检查用户是否存在
	 */
	@Override
	public void execute(final UserRegisterDto userRegisterDto) {
		userService.doExist(userRegisterDto.getMobile());
	}

	/**
	 * 返回父处理程序的执行顺序
	 *
	 * @return 执行顺序的整数值
	 * 该方法返回1，表示该处理器的父处理程序的执行顺序
	 */
	@Override
	public Integer executeParentOrder() {
		return 1;
	}

	/**
	 * 返回执行层级
	 *
	 * @return 执行层级的整数值
	 * 该方法返回2，表示该处理器的执行层级
	 */
	@Override
	public Integer executeTier() {
		return 2;
	}

	/**
	 * 返回执行顺序
	 *
	 * @return 执行顺序的整数值
	 * 该方法返回2，表示该处理器的执行顺序
	 */
	@Override
	public Integer executeOrder() {
		return 2;
	}
}
