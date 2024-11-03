package com.damai.enums;

/**
 * 业务状态枚举类，用于表示业务中的是/否状态
 */
public enum BusinessStatus {
    /**
     * 通用状态枚举
     */
    YES(1, "是"),
    NO(0, "否");

    // 状态码
    private Integer code;

    // 状态消息
    private String msg;

    /**
     * 构造方法，初始化业务状态的代码和消息
     *
     * @param code 状态码
     * @param msg  状态消息
     */
    BusinessStatus(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 获取状态码
     *
     * @return 状态码
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 获取状态消息
     *
     * @return 状态消息，如果未定义则返回空字符串
     */
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }

    /**
     * 根据状态码获取对应的状态消息
     *
     * @param code 状态码
     * @return 对应的状态消息，如果找不到匹配的状态码则返回空字符串
     */
    public static String getMsg(Integer code) {
        for (BusinessStatus re : BusinessStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }

    /**
     * 根据状态码获取对应的业务状态枚举
     *
     * @param code 状态码
     * @return 对应的业务状态枚举，如果找不到匹配的状态码则返回null
     */
    public static BusinessStatus getRc(Integer code) {
        for (BusinessStatus re : BusinessStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re;
            }
        }
        return null;
    }
}
