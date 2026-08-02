package com.livetix.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_real_name_info")
public class RealNameInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String realName;
    private String idCardType;    // ID_CARD/PASSPORT/HK_MACAU_PASS
    private String idCardNumber;  // encrypted
    private String phone;
    private Integer isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
