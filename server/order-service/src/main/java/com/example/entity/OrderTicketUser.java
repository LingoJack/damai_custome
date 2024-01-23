package com.example.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.data.BaseData;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * <p>
 * 购票人订单表
 * </p>
 *
 * @author k
 * @since 2024-01-12
 */
@Data
@TableName("d_order_ticket_user")
public class OrderTicketUser extends BaseData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键id
     */
    private Long id;

    /**
     * 订单id
     */
    private Long orderId;

    /**
     * 节目表id
     */
    private Long programId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 购票人id
     */
    private Long ticketUserId;

    /**
     * 座位id
     */
    private Long seatId;

    /**
     * 订单价格
     */
    private BigDecimal orderPrice;

    /**
     * 支付订单价格
     */
    private BigDecimal payOrderPrice;

    /**
     * 支付订单方式
     */
    private Integer payOrderType;

    /**
     * 订单状态 1:未支付 2:已取消 3:已支付 4:已退单
     */
    private Integer orderStatus;

    /**
     * 生成订单时间
     */
    private Date createOrderTime;

    /**
     * 取消订单时间
     */
    private Date cancelOrderTime;

    /**
     * 支付订单时间
     */
    private Date payOrderTime;
}