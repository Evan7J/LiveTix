package com.livetix.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_venue")
public class Venue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String city;
    private String address;
    private String seatMap;   // JSON
    private Integer totalSeats;
    private String contactPhone;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
