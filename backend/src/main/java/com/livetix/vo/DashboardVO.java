package com.livetix.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class DashboardVO {

    /** Total box office revenue */
    private BigDecimal totalRevenue;

    /** Total order count */
    private Long totalOrderCount;

    /** Total user count */
    private Long totalUserCount;

    /** Active shows count */
    private Long activeShowCount;

    /** 30-day revenue trend (date -> amount) */
    private List<Map<String, Object>> revenueTrend;

    /** Hot show ranking */
    private List<Map<String, Object>> hotShows;

    /** Latest orders */
    private List<Map<String, Object>> latestOrders;
}
