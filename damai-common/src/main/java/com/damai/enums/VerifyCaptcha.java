package com.damai.enums;

/**
 * 验证码校验枚举类
 * 用于定义是否需要进行验证码校验
 */
public enum VerifyCaptcha {

    /**
     * 是否需要校验验证码
     */
    NO(0, "no", "不需要"),

    YES(1, "yes", "需要"),

    ;

    // 验证码校验状态的代码
    private Integer code;

    // 验证码校验状态的值
    private String value;

    // 验证码校验状态的描述信息
    private String msg;

    /**
     * 构造函数，初始化验证码校验状态
     *
     * @param code  验证码校验状态的代码
     * @param value 验证码校验状态的值
     * @param msg   验证码校验状态的描述信息
     */
    VerifyCaptcha(Integer code, String value, String msg) {
        this.code = code;
        this.value = value;
        this.msg = msg;
    }

    /**
     * 获取验证码校验状态的代码
     *
     * @return 验证码校验状态的代码
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 设置验证码校验状态的代码
     *
     * @param code 验证码校验状态的代码
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * 获取验证码校验状态的描述信息
     *
     * @return 验证码校验状态的描述信息，如果未定义则返回空字符串
     */
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }

    /**
     * 设置验证码校验状态的描述信息
     *
     * @param msg 验证码校验状态的描述信息
     */
    public void setMsg(String msg) {
        this.msg = msg;
    }

    /**
     * 获取验证码校验状态的值
     *
     * @return 验证码校验状态的值
     */
    public String getValue() {
        return value;
    }

    /**
     * 设置验证码校验状态的值
     *
     * @param value 验证码校验状态的值
     */
    public void setValue(final String value) {
        this.value = value;
    }

    /**
     * 根据代码获取对应的描述信息
     *
     * @param code 验证码校验状态的代码
     * @return 对应的描述信息，如果未找到则返回空字符串
     */
    public static String getMsg(Integer code) {
        for (VerifyCaptcha re : VerifyCaptcha.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }

    /**
     * 根据代码获取对应的验证码校验状态
     *
     * @param code 验证码校验状态的代码
     * @return 对应的验证码校验状态，如果未找到则返回null
     */
    public static VerifyCaptcha getRc(Integer code) {
        for (VerifyCaptcha re : VerifyCaptcha.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re;
            }
        }
        return null;
    }
}
