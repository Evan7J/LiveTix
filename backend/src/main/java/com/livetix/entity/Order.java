package com.livetix.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private Long showId;
    private String showTitle;
    private String venueName;
    private LocalDateTime showTime;
    private String coverImage;
    private String ticketType;
    private BigDecimal ticketPrice;
    private Integer quantity;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private String status;       // pending/paid/cancelled/refunded
    private String payMethod;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    private LocalDateTime refundTime;
    private Long couponId;
    private Long sessionId;             // 关联场次ID
    private String realNameIds;         // 观演人ID列表(JSON数组)
    private String seats;               // 座位标签 "A排3座"
    private LocalDateTime payExpireTime; // 支付超时时间
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    // ===== 退票相关（transient，从 Show 表关联填充，不存入数据库）=====
    @TableField(exist = false)
    private Integer allowRefund;          // 是否支持退票

    @TableField(exist = false)
    private Integer refundDeadlineHours;  // 退票截止时间（开演前N小时）

    @TableField(exist = false)
    private java.math.BigDecimal refundFeePercent;  // 退票手续费率
}
