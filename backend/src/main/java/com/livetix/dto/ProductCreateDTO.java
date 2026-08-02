package com.livetix.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品发布/编辑请求DTO
 *
 * 安全设计：
 *   1. 只暴露允许用户填写的字段，防止客户端注入 userId/status/viewCount 等敏感字段
 *   2. 使用 @Valid 校验必填项和长度限制
 *   3. 后端从 Sa-Token 获取当前登录用户ID，不信任客户端传入的 userId
 */
@Data
public class ProductCreateDTO {

    @NotBlank(message = "商品标题不能为空")
    @Size(min = 2, max = 256, message = "标题长度2-256位")
    private String title;

    @Size(max = 5000, message = "描述最多5000字")
    private String description;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    /** 原价（可选） */
    private BigDecimal originalPrice;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    /** 图片列表，JSON 数组字符串 */
    private String images;

    /** 封面图URL */
    private String coverImage;

    /** 新旧程度：1全新 2几乎全新 3轻微使用 4明显使用 */
    private Integer conditionLevel;

    /** 交易地点 */
    @Size(max = 256, message = "交易地点最长256位")
    private String tradeLocation;

    /** 是否可议价：0否 1是 */
    private Integer isNegotiable;
}