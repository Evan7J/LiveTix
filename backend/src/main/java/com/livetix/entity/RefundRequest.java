package com.livetix.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_refund_request")
public class RefundRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long userId;
    private String reason;
    private BigDecimal refundAmount;
    private BigDecimal feeAmount;
    private String status;         // pending/approved/rejected
    private Long reviewerId;
    private String reviewComment;
    private LocalDateTime reviewTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // === 关联数据（仅用于返回给前端，非数据库字段） ===
    @TableField(exist = false)
    private String orderNo;
    @TableField(exist = false)
    private String showTitle;
    @TableField(exist = false)
    private String ticketType;
    @TableField(exist = false)
    private Integer quantity;
    @TableField(exist = false)
    private String username;

    @TableLogic
    private Integer deleted;
}
