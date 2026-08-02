package com.livetix.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.livetix.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * Dashboard: total box office revenue
     */
    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM t_order WHERE status = 'paid'")
    BigDecimal totalRevenue();

    /**
     * Dashboard: total order count
     */
    @Select("SELECT COUNT(*) FROM t_order")
    Long totalOrderCount();

    /**
     * Dashboard: revenue trend (last 30 days)
     */
    @Select("SELECT DATE(create_time) as date, COALESCE(SUM(pay_amount), 0) as amount " +
            "FROM t_order WHERE status = 'paid' " +
            "AND create_time >= #{startDate} " +
            "GROUP BY DATE(create_time) ORDER BY date")
    List<Map<String, Object>> revenueTrend(@Param("startDate") LocalDateTime startDate);

    /**
     * Dashboard: hot show ranking
     */
    @Select("SELECT show_title as name, COUNT(*) as sales, COALESCE(SUM(pay_amount), 0) as revenue " +
            "FROM t_order WHERE status = 'paid' " +
            "GROUP BY show_id, show_title ORDER BY sales DESC LIMIT #{limit}")
    List<Map<String, Object>> hotShowRanking(@Param("limit") int limit);

    /**
     * Dashboard: latest orders
     */
    @Select("SELECT o.*, u.nickname as user_nickname " +
            "FROM t_order o LEFT JOIN t_user u ON o.user_id = u.id " +
            "ORDER BY o.create_time DESC LIMIT #{limit}")
    List<Map<String, Object>> latestOrders(@Param("limit") int limit);

    /**
     * 统计用户在指定演出下已购票数（已支付 + 待支付）
     * 用于限购校验
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM t_order " +
            "WHERE user_id = #{userId} AND show_id = #{showId} " +
            "AND status IN ('paid', 'pending')")
    int countUserTicketsForShow(@Param("userId") Long userId, @Param("showId") Long showId);
}
