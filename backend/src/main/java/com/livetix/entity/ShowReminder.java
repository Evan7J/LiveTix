package com.livetix.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_show_reminder")
public class ShowReminder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long showId;
    private Integer isReminded;   // 0未提醒 1已提醒

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
