package com.livetix.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_show_favorite")
public class ShowFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long showId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
