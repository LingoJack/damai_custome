package com.damai.enums;

/**
 * 组合模式类型
 **/
public enum CompositeCheckType {
    /**
     * 用户注册检查
     */
    USER_REGISTER_CHECK(1, "user_register_check", "用户注册"),

    /**
     * 节目详情查看检查
     */
    PROGRAM_DETAIL_CHECK(2, "program_detail_check", "节目详情"),

    /**
     * 订单创建检查
     */
    PROGRAM_ORDER_CREATE_CHECK(3, "program_order_create_check", "订单创建"),

    /**
     * 节目推荐检查
     */
    PROGRAM_RECOMMEND_CHECK(4, "program_recommend_check", "节目推荐");

    // 枚举成员变量
    private Integer code;
    private String value;
    private String msg;

    // 构造方法
    CompositeCheckType(Integer code, String value, String msg) {
        this.code = code;
        this.value = value;
        this.msg = msg;
    }

    // 获取code值
    public Integer getCode() {
        return code;
    }

    // 设置code值
    public void setCode(Integer code) {
        this.code = code;
    }

    // 获取msg值
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }

    // 设置msg值
    public void setMsg(String msg) {
        this.msg = msg;
    }

    // 获取value值
    public String getValue() {
        return value;
    }

    // 设置value值
    public void setValue(final String value) {
        this.value = value;
    }

    /**
     * 根据code获取对应的msg
     *
     * @param code 需要查找的代码
     * @return 对应的msg，如果找不到则返回空字符串
     */
    public static String getMsg(Integer code) {
        for (CompositeCheckType re : CompositeCheckType.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }

    /**
     * 根据code获取对应的枚举类型
     *
     * @param code 需要查找的代码
     * @return 对应的枚举类型，如果找不到则返回null
     */
    public static CompositeCheckType getByCode(Integer code) {
        for (CompositeCheckType compositeCheckType : CompositeCheckType.values()) {
            if (compositeCheckType.code.intValue() == code.intValue()) {
                return compositeCheckType;
            }
        }
        return null;
    }
}
