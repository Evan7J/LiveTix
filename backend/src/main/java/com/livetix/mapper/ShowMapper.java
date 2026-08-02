package com.livetix.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.livetix.entity.Show;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShowMapper extends BaseMapper<Show> {

    /**
     * Deduct stock with optimistic lock (atomic SQL)
     * Only succeeds when available_stock >= quantity
     */
    @Update("UPDATE t_show SET available_stock = available_stock - #{quantity} " +
            "WHERE id = #{showId} AND available_stock >= #{quantity}")
    int deductStock(@Param("showId") Long showId, @Param("quantity") int quantity);

    /**
     * P0-5: 安全库存恢复 — 带 total_stock 上限校验
     * 防止并发取消/定时任务导致库存数超过总库存
     */
    @Update("UPDATE t_show SET available_stock = available_stock + #{quantity} " +
            "WHERE id = #{showId} AND available_stock + #{quantity} <= total_stock")
    int restoreStockSafe(@Param("showId") Long showId, @Param("quantity") int quantity);

    /**
     * Restore stock on order cancel (legacy — no upper bound check)
     * @deprecated 推荐使用 {@link #restoreStockSafe(Long, int)} 避免库存超量恢复
     */
    @Deprecated
    @Update("UPDATE t_show SET available_stock = available_stock + #{quantity} WHERE id = #{showId}")
    int restoreStock(@Param("showId") Long showId, @Param("quantity") int quantity);

    /**
     * List shows with multi-condition filters (city, timeRange, sort)
     * Uses LEFT JOIN on t_venue for city filtering
     */
    @org.apache.ibatis.annotations.Select("<script>" +
        "SELECT s.*, v.name AS venueName, v.city AS venueCity, c.name AS categoryName " +
        "FROM t_show s " +
        "LEFT JOIN t_venue v ON s.venue_id = v.id " +
        "LEFT JOIN t_category c ON s.category_id = c.id " +
        "WHERE s.deleted = 0 AND s.status IN (0, 1) " +
        "<if test='categoryId != null'>AND s.category_id = #{categoryId}</if> " +
        "<if test='city != null and city != \"\"'>AND v.city = #{city}</if> " +
        "<if test='timeRange == \"today\"'>AND DATE(s.show_time) = CURDATE()</if> " +
        "<if test='timeRange == \"tomorrow\"'>AND DATE(s.show_time) = DATE_ADD(CURDATE(), INTERVAL 1 DAY)</if> " +
        "<if test='timeRange == \"weekend\"'>AND s.show_time BETWEEN CONCAT(DATE_ADD(CURDATE(), INTERVAL (6 - WEEKDAY(CURDATE())) DAY), ' 00:00:00') AND CONCAT(DATE_ADD(CURDATE(), INTERVAL (7 - WEEKDAY(CURDATE())) DAY), ' 23:59:59')</if> " +
        "<if test='timeRange == \"month\"'>AND s.show_time BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 1 MONTH)</if> " +
        "<if test='timeRange == \"calendar\" and date != null and date != \"\"'>AND DATE(s.show_time) = #{date}</if> " +
        "<if test='keyword != null and keyword != \"\"'>AND (s.title LIKE CONCAT('%', #{keyword}, '%') OR s.artists LIKE CONCAT('%', #{keyword}, '%'))</if> " +
        "<choose>" +
        "  <when test='sort == \"soonest\"'>ORDER BY s.show_time ASC</when>" +
        "  <when test='sort == \"latest\"'>ORDER BY s.create_time DESC</when>" +
        "  <otherwise>ORDER BY s.sort DESC, s.is_hot DESC, s.create_time DESC</otherwise>" +
        "</choose> " +
        "LIMIT #{offset}, #{pageSize}" +
        "</script>")
    java.util.List<Show> selectShowsWithFilters(
        @Param("categoryId") Long categoryId,
        @Param("city") String city,
        @Param("timeRange") String timeRange,
        @Param("date") String date,
        @Param("keyword") String keyword,
        @Param("sort") String sort,
        @Param("offset") int offset,
        @Param("pageSize") int pageSize);

    /**
     * Count shows with same filters (for pagination total)
     * 47 修复: @Select 注解正确放置在方法上面
     */
    @org.apache.ibatis.annotations.Select("<script>" +
        "SELECT COUNT(*) FROM t_show s " +
        "LEFT JOIN t_venue v ON s.venue_id = v.id " +
        "WHERE s.deleted = 0 AND s.status IN (0, 1) " +
        "<if test='categoryId != null'>AND s.category_id = #{categoryId}</if> " +
        "<if test='city != null and city != \"\"'>AND v.city = #{city}</if> " +
        "<if test='timeRange == \"today\"'>AND DATE(s.show_time) = CURDATE()</if> " +
        "<if test='timeRange == \"tomorrow\"'>AND DATE(s.show_time) = DATE_ADD(CURDATE(), INTERVAL 1 DAY)</if> " +
        "<if test='timeRange == \"weekend\"'>AND s.show_time BETWEEN CONCAT(DATE_ADD(CURDATE(), INTERVAL (6 - WEEKDAY(CURDATE())) DAY), ' 00:00:00') AND CONCAT(DATE_ADD(CURDATE(), INTERVAL (7 - WEEKDAY(CURDATE())) DAY), ' 23:59:59')</if> " +
        "<if test='timeRange == \"month\"'>AND s.show_time BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 1 MONTH)</if> " +
        "<if test='timeRange == \"calendar\" and date != null and date != \"\"'>AND DATE(s.show_time) = #{date}</if> " +
        "<if test='keyword != null and keyword != \"\"'>AND (s.title LIKE CONCAT('%', #{keyword}, '%') OR s.artists LIKE CONCAT('%', #{keyword}, '%'))</if> " +
        "</script>")
    long countShowsWithFilters(
        @Param("categoryId") Long categoryId,
        @Param("city") String city,
        @Param("timeRange") String timeRange,
        @Param("date") String date,
        @Param("keyword") String keyword);

    /**
     * S5: 批量增量更新浏览量（一条 SQL 替代逐条 updateById）
     */
    @Update("<script>" +
        "INSERT INTO t_show (id, view_count) VALUES " +
        "<foreach collection='list' item='item' separator=','>" +
        "(#{item.id}, #{item.count})" +
        "</foreach>" +
        " ON DUPLICATE KEY UPDATE view_count = view_count + VALUES(view_count)" +
        "</script>")
    void batchIncrementViewCount(@Param("list") java.util.List<java.util.Map<String, Object>> items);
}
