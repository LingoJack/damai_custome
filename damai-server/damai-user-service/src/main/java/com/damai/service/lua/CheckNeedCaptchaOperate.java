package com.damai.service.lua;

import com.damai.initialize.base.AbstractApplicationPostConstructHandler;
import com.damai.redis.RedisCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CheckNeedCaptchaOperate extends AbstractApplicationPostConstructHandler {

	@Autowired
	private RedisCache redisCache;

	private DefaultRedisScript<String> redisScript;

	@Override
	public Integer executeOrder() {
		// 定义该处理器的执行顺序，数值越小越先执行
		return 1;
	}

	@Override
	public void executeInit(final ConfigurableApplicationContext context) {
		// 初始化 Redis 脚本
		try {
			redisScript = new DefaultRedisScript<>();
			redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/checkNeedCaptcha.lua")));
			redisScript.setResultType(String.class);
		}
		catch (Exception e) {
			// 记录初始化 Redis 脚本时发生的错误
			log.error("redisScript init lua error", e);
		}
	}

	/**
	 * 检查是否需要进行验证码操作
	 * 该方法通过执行存储在 Redis 中的 Lua 脚本来判断给定的条件是否满足进行验证码操作的标准。
	 * 主要用于确定在当前情境下，用户是否需要进行验证码验证。
	 *
	 * @param keys 用于执行 Redis 脚本的键列表，通常包含用户标识或其他相关键
	 * @param args 传递给 Redis 脚本的参数数组，可以包含影响脚本执行逻辑的额外信息
	 * @return 返回一个布尔值，指示是否需要进行验证码操作。true 表示需要，false 表示不需要
	 */
	public Boolean checkNeedCaptchaOperate(List<String> keys, String[] args) {
		// 执行存储在 Redis 中的 Lua 脚本，根据给定的键和参数获取脚本执行结果
		Object object = redisCache.getInstance().execute(redisScript, keys, args);

		// 将脚本执行结果转换为布尔值并返回
		return Boolean.parseBoolean((String) object);
	}
}

