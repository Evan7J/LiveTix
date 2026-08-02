package com.livetix.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderCreateDTO {

    @NotNull(message = "演出ID不能为空")
    private Long showId;

    @NotNull(message = "票种不能为空")
    private String ticketType;

    @NotNull(message = "票价不能为空")
    private Double ticketPrice;

    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity;

    /**
     * 5: 请求幂等性标识 — 前端生成 UUID，后端 Redis SET NX 校验
     * 同一 requestId 5 分钟内只处理一次
     */
    private String requestId;

    private String remark;        // optional
    private String seats;         // 座位标签，如 "A排3座"
    private String seatCells;     // 座位坐标，如 "1-12,1-13"（r-c格式，用于标记已售）
    private Long realNameId;      // 观演人ID（实名制演出必填）
}
