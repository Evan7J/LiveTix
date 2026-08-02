package com.livetix.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_admin_log")
public class AdminLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long adminId;
    private String adminName;
    private String module;        // show/order/user/coupon/category/refund/system
    private String action;        // create/update/delete/approve/reject
    private Long targetId;
    private String detail;        // JSON
    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
