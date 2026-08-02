package com.livetix.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_wallet_transaction")
public class WalletTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String type;          // recharge/purchase/refund/withdraw
    private BigDecimal amount;    // positive=入账, negative=出账
    private BigDecimal balanceAfter;
    private Long orderId;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
