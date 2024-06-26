package com.damai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.damai.data.BaseTableData;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 支付账单 实体
 * @author: 阿星不是程序员
 **/
@Data
@TableName("d_pay_bill")
public class PayBill extends BaseTableData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键id
     */
    private Long id;

    /**
     * 支付流水号
     */
    private String payNumber;

    /**
     * 商户订单号
     */
    private String outOrderNo;

    /**
     * 支付渠道
     */
    private String payChannel;

    /**
     * 支付环境
     */
    private String payScene;

    /**
     * 订单标题
     */
    private String subject;

    /**
     * 三方交易凭证号
     */
    private String tradeNumber;

    /**
     * 支付金额
     */
    private BigDecimal payAmount;
    
    /**
     * 支付种类
     * */
    private Integer payBillType;

    /**
     * 支付状态
     */
    private Integer payBillStatus;

    /**
     * 支付时间
     */
    private Date payTime;
}