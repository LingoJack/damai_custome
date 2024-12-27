package com.damai.repeatexecutelimit.annotion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * 防重复幂等 注解
 **/
@Target(value = {ElementType.TYPE, ElementType.METHOD})
@Retention(value = RetentionPolicy.RUNTIME)
public @interface RepeatExecuteLimit {

	/**
	 * 业务名称
	 *
	 * @return name
	 */
	String name() default "";

	/**
	 * key设置
	 *
	 * @return key
	 */
	String[] keys();

	/**
	 * 在多长时间内一直保持幂等，如果不配置则以执行方法为准
	 * 如果此值为0，则幂等标识相当于不起任何作用
	 */
	long durationTime() default 0L;

	/**
	 * 当消息执行已经出发防重复执行的限制时，提示信息
	 */
	String message() default "提交频繁，请稍后重试";

}
