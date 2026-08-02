package com.livetix.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_show")
public class Show {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private Long categoryId;
    private Long venueId;
    private String coverImage;
    private String images;        // JSON string
    private String description;
    private String artists;
    private LocalDateTime showTime;
    private LocalDateTime endTime;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private Integer totalStock;
    private Integer availableStock;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private String ticketTypes;   // JSON string
    private Integer status;       // 0待上架 1在售 2售罄 3已结束 4已取消
    private Integer isHot;
    private Integer isRecommend;
    private Integer sort;
    private Integer viewCount;

    // === 新增业务属性 ===
    private Integer buyLimit;           // 限购数量 0不限购
    private Integer isRealName;         // 是否实名制 0否 1是
    private Integer allowRefund;        // 是否允许退票
    private Integer refundDeadlineHours; // 退票时限(距开演小时)
    private BigDecimal refundFeePercent; // 退票手续费比例
    private Integer allowTransfer;      // 是否支持转赠
    private Integer payTimeoutMinutes;  // 支付超时(分钟)
    private Integer enableReminder;     // 是否允许开售提醒
    private String showStatus;          // 演出状态 upcoming/presale/onsale/soldout/ended
    private String rules;               // 票务规则说明
    private String notice;              // 观演须知
    private String refundPolicy;        // 退票政策

    // === 非数据库字段（仅用于返回给前端） ===
    @TableField(exist = false)
    private String venueName;           // 场馆名称（关联查询）
    @TableField(exist = false)
    private String venueCity;           // 场馆所在城市
    @TableField(exist = false)
    private String categoryName;        // 分类名称

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
