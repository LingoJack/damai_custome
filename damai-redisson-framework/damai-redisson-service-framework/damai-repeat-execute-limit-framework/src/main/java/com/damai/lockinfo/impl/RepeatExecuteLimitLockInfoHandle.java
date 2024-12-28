package com.damai.lockinfo.impl;

import com.damai.lockinfo.AbstractLockInfoHandle;

/**
 * 锁信息实现(防重复幂等)
 **/
public class RepeatExecuteLimitLockInfoHandle extends AbstractLockInfoHandle {

	public static final String PREFIX_NAME = "REPEAT_EXECUTE_LIMIT";

	@Override
	protected String getLockPrefixName() {
		return PREFIX_NAME;
	}
}
