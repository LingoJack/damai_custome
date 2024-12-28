package com.damai.service.composite.register.impl;

import com.damai.captcha.model.common.ResponseModel;
import com.damai.captcha.model.vo.CaptchaVO;
import com.damai.core.RedisKeyManage;
import com.damai.dto.UserRegisterDto;
import com.damai.enums.BaseCode;
import com.damai.enums.VerifyCaptcha;
import com.damai.exception.DaMaiFrameException;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.CaptchaHandle;
import com.damai.service.composite.register.AbstractUserRegisterCheckHandler;
import com.damai.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户注册验证码验证处理器
 * 继承自AbstractUserRegisterCheckHandler，用于在用户注册时验证验证码
 */
@Slf4j
@Component
public class UserRegisterVerifyCaptcha extends AbstractUserRegisterCheckHandler {

	// 注入验证码处理类
	@Autowired
	private CaptchaHandle captchaHandle;

	// 注入Redis缓存类
	@Autowired
	private RedisCache redisCache;

	/**
	 * 执行验证码验证逻辑
	 *
	 * @param param 用户注册DTO，包含用户输入的密码、确认密码和验证码等信息
	 * @throws DaMaiFrameException 如果验证失败，抛出此异常
	 */
	@Override
	protected void execute(UserRegisterDto param) {
		// 检查两次输入的密码是否一致
		String password = param.getPassword();
		String confirmPassword = param.getConfirmPassword();
		if (!password.equals(confirmPassword)) {
			throw new DaMaiFrameException(BaseCode.TWO_PASSWORDS_DIFFERENT);
		}

		// 从Redis中获取验证码标识
		String verifyCaptcha = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.VERIFY_CAPTCHA_ID, param.getCaptchaId()), String.class);
		if (StringUtil.isEmpty(verifyCaptcha)) {
			throw new DaMaiFrameException(BaseCode.VERIFY_CAPTCHA_ID_NOT_EXIST);
		}

		// 如果验证码标识存在且为YES，进一步验证用户输入的验证码
		if (VerifyCaptcha.YES.getValue().equals(verifyCaptcha)) {
			String captchaVerification = param.getCaptchaVerification();

			// 若用户输入的验证码为空，报异常
			if (StringUtil.isEmpty(captchaVerification)) {
				throw new DaMaiFrameException(BaseCode.VERIFY_CAPTCHA_EMPTY);
			}

			// 输出用户输入的验证码日志
			log.info("传入的captchaVerification:{}", captchaVerification);

			// 创建验证码验证对象并调用验证码处理类进行验证，不成功则报异常
			CaptchaVO captchaVO = new CaptchaVO();
			captchaVO.setCaptchaVerification(captchaVerification);
			ResponseModel responseModel = captchaHandle.verification(captchaVO);
			if (!responseModel.isSuccess()) {
				throw new DaMaiFrameException(responseModel.getRepCode(), responseModel.getRepMsg());
			}
		}
	}

	/**
	 * 返回父处理顺序
	 *
	 * @return 父处理顺序的整数值
	 */
	@Override
	public Integer executeParentOrder() {
		return 0;
	}

	/**
	 * 返回执行层级
	 *
	 * @return 执行层级的整数值
	 */
	@Override
	public Integer executeTier() {
		return 1;
	}

	/**
	 * 返回执行顺序
	 *
	 * @return 执行顺序的整数值
	 */
	@Override
	public Integer executeOrder() {
		return 1;
	}
}

