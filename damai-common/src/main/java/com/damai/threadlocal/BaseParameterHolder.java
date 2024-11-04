package com.damai.threadlocal;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * BaseParameterHolder类用于在ThreadLocal中存储和管理每个线程独有的参数
 * 它提供了一组静态方法，用于设置、获取和移除线程本地参数
 */
public class BaseParameterHolder {

    // 定义一个ThreadLocal变量，用于存储线程本地的参数映射
    private static final ThreadLocal<Map<String, String>> THREAD_LOCAL_MAP = new ThreadLocal<>();

    /**
     * 设置线程本地参数
     *
     * @param name  参数名
     * @param value 参数值
     */
    public static void setParameter(String name, String value) {
        Map<String, String> map = THREAD_LOCAL_MAP.get();
        if (map == null) {
            map = new HashMap<>(64);
        }
        map.put(name, value);
        THREAD_LOCAL_MAP.set(map);
    }

    /**
     * 获取线程本地参数
     *
     * @param name 参数名
     * @return 参数值，如果不存在则返回null
     */
    public static String getParameter(String name) {
        return Optional.ofNullable(THREAD_LOCAL_MAP.get()).map(map -> map.get(name)).orElse(null);
    }

    /**
     * 移除线程本地参数
     *
     * @param name 参数名
     */
    public static void removeParameter(String name) {
        Map<String, String> map = THREAD_LOCAL_MAP.get();
        if (map != null) {
            map.remove(name);
        }
    }

    /**
     * 获取ThreadLocal变量
     *
     * @return ThreadLocal变量，用于存储线程本地参数映射
     */
    public static ThreadLocal<Map<String, String>> getThreadLocal() {
        return THREAD_LOCAL_MAP;
    }

    /**
     * 获取线程本地参数映射
     * 如果当前线程没有参数映射，则创建一个新的映射并返回
     *
     * @return 线程本地参数映射
     */
    public static Map<String, String> getParameterMap() {
        Map<String, String> map = THREAD_LOCAL_MAP.get();
        if (map == null) {
            map = new HashMap<>(64);
        }
        return map;
    }

    /**
     * 设置线程本地参数映射
     *
     * @param map 参数映射
     */
    public static void setParameterMap(Map<String, String> map) {
        THREAD_LOCAL_MAP.set(map);
    }

    /**
     * 移除线程本地参数映射
     */
    public static void removeParameterMap() {
        THREAD_LOCAL_MAP.remove();
    }
}
