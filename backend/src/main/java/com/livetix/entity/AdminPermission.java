package com.livetix.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_admin_permission")
public class AdminPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String permCode;
    private String permName;
    private Long parentId;
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
