package com.livetix.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_show_session")
public class ShowSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long showId;
    private String sessionName;
    private LocalDateTime showTime;
    private LocalDateTime endTime;
    private Integer totalStock;
    private Integer availableStock;
    private String ticketTypes;   // JSON
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
