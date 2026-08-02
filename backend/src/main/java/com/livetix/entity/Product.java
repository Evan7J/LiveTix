package com.livetix.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体
 * conditionLevel: 1全新 2几乎全新 3轻微使用 4明显使用
 * status: 1在售 2已售 3下架
 * isNegotiable: 0不议价 1可议价
 */
@Data
@TableName("t_product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品标题 */
    private String title;

    /** 商品描述（富文本或纯文本） */
    private String description;

    /** 售价 */
    private BigDecimal price;

    /** 原价（可为空，用于展示"原价¥XX"的折扣效果） */
    private BigDecimal originalPrice;

    /** 分类ID，关联 t_category.id */
    private Long categoryId;

    /** 卖家用户ID，关联 t_user.id */
    private Long userId;

    /** 图片列表，JSON 数组格式：["url1","url2"] */
    private String images;

    /** 封面图（列表展示用，取 images 第一张或单独上传） */
    private String coverImage;

    /** 新旧程度：1全新 2几乎全新 3轻微使用 4明显使用 */
    private Integer conditionLevel;

    /** 交易地点（如：XX大学XX校区XX楼） */
    private String tradeLocation;

    /** 商品状态：1在售 2已售 3下架 */
    private Integer status;

    /** 浏览量（Redis 定时批量刷回） */
    private Integer viewCount;

    /** 收藏数 */
    private Integer favoriteCount;

    /** 是否可议价：0否 1是 */
    private Integer isNegotiable;

    /* ===== 非数据库字段（关联查询填充） ===== */

    /** 分类名称（关联查询，不存库） */
    @TableField(exist = false)
    private String categoryName;

    /** 卖家昵称（关联查询，不存库） */
    @TableField(exist = false)
    private String sellerNickname;

    /** 卖家头像（关联查询，不存库） */
    @TableField(exist = false)
    private String sellerAvatar;

    /* ===== 自动填充字段 ===== */

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0未删除 1已删除 */
    @TableLogic
    private Integer deleted;
}