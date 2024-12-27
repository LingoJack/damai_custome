package com.damai.util;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;

/**
 * 提供远程请求相关工具方法的类
 */
public class RemoteUtil {

	/**
	 * 获取远程请求的唯一标识
	 *
	 * @param request HttpServletRequest对象，用于获取请求头信息
	 * @return 字符串，表示远程请求的唯一标识
	 */
	public static String getRemoteId(HttpServletRequest request) {
		// 获取请求头中的'X-Forwarded-For'字段，用于识别经过代理后的客户端IP
		String forward = request.getHeader("X-Forwarded-For");
		// 从'X-Forwarded-For'中提取真实的客户端IP地址
		String ip = getRemoteIpFromForward(forward);
		// 获取请求头中的'user-agent'字段，用于标识用户代理
		String ua = request.getHeader("user-agent");
		// 如果IP地址不为空，则将IP地址与用户代理拼接作为远程请求的唯一标识
		if (StringUtils.isNotBlank(ip)) {
			return ip + ua;
		}
		// 如果IP地址为空，则将服务器端看到的请求地址与用户代理拼接作为远程请求的唯一标识
		return request.getRemoteAddr() + ua;
	}

	/**
	 * 从请求头的'X-Forwarded-For'字段中提取真实的客户端IP地址
	 *
	 * @param forward 'X-Forwarded-For'请求头的值
	 * @return 字符串，表示真实的客户端IP地址
	 */
	private static String getRemoteIpFromForward(String forward) {
		// 如果'X-Forwarded-For'字段的值不为空，则处理该值以提取IP地址
		if (StringUtils.isNotBlank(forward)) {
			// 分割'X-Forwarded-For'字段值以逗号，获取第一个IP地址，即客户端真实IP
			String[] ipList = forward.split(",");
			// 去除第一个IP地址两端的空白字符并返回
			return StringUtils.trim(ipList[0]);
		}
		// 如果'X-Forwarded-For'字段的值为空，则返回null
		return null;
	}
}
