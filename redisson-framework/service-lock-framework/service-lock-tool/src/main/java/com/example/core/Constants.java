package com.example.core;

import java.io.Serializable;

/**
 * @program: redis-example
 * @description: 常量类
 * @author: 星哥
 * @create: 2023-05-28
 **/
public class Constants implements Serializable {

    private static final long serialVersionUID = 6582985503920120895L;

    //防重复提交的用户标识
    public static final String REPEAT_LIMIT_USERID = "repeatLimitUserId";

    public static final String SEPARATOR = ":";
}