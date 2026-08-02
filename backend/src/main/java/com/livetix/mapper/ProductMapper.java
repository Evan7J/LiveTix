package com.livetix.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.livetix.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 商品数据访问层
 *
 * 核心方法：
 *   batchIncrementViewCount — 批量增量更新浏览量，一条 SQL 替代逐条 updateById
 *   selectProductsWithFilters — 多条件联合查询（分类、关键词、新旧、排序）
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 批量增量更新浏览量
     * 使用 ON DUPLICATE KEY UPDATE 实现原子累加，避免 READ + WRITE 并发覆盖
     *
     * 面试点：MySQL 的 INSERT ... ON DUPLICATE KEY UPDATE 在批量场景下
     * 比逐条 UPDATE 效率高 N 倍，且是原子操作，无并发问题
     */
    @Update("<script>" +
        "INSERT INTO t_product (id, view_count) VALUES " +
        "<foreach collection='list' item='item' separator=','>" +
        "(#{item.id}, #{item.count})" +
        "</foreach>" +
        " ON DUPLICATE KEY UPDATE view_count = view_count + VALUES(view_count)" +
        "</script>")
    void batchIncrementViewCount(@Param("list") List<Map<String, Object>> items);

    /**
     * 多条件商品列表查询（带关联查询分类名和卖家昵称）
     *
     * 条件支持：categoryId、keyword（标题搜索）、conditionLevel、status、sort
     * 排序支持：newest（最新）、price_asc（价格升序）、price_desc（价格降序）
     */
    @org.apache.ibatis.annotations.Select("<script>" +
        "SELECT p.*, c.name AS categoryName, u.nickname AS sellerNickname, u.avatar AS sellerAvatar " +
        "FROM t_product p " +
        "LEFT JOIN t_category c ON p.category_id = c.id " +
        "LEFT JOIN t_user u ON p.user_id = u.id " +
        "WHERE p.deleted = 0 " +
        "<if test='categoryId != null'>AND p.category_id = #{categoryId}</if> " +
        "<if test='status != null'>AND p.status = #{status}</if> " +
        "<if test='conditionLevel != null'>AND p.condition_level = #{conditionLevel}</if> " +
        "<if test='keyword != null and keyword != \"\"'>AND p.title LIKE CONCAT('%', #{keyword}, '%')</if> " +
        "<choose>" +
        "  <when test='sort == \"price_asc\"'>ORDER BY p.price ASC</when>" +
        "  <when test='sort == \"price_desc\"'>ORDER BY p.price DESC</when>" +
        "  <otherwise>ORDER BY p.create_time DESC</otherwise>" +
        "</choose> " +
        "LIMIT #{offset}, #{pageSize}" +
        "</script>")
    List<Product> selectProductsWithFilters(
        @Param("categoryId") Long categoryId,
        @Param("status") Integer status,
        @Param("conditionLevel") Integer conditionLevel,
        @Param("keyword") String keyword,
        @Param("sort") String sort,
        @Param("offset") int offset,
        @Param("pageSize") int pageSize);

    /**
     * 统计符合筛选条件的商品总数（用于分页）
     */
    @org.apache.ibatis.annotations.Select("<script>" +
        "SELECT COUNT(*) FROM t_product p " +
        "WHERE p.deleted = 0 " +
        "<if test='categoryId != null'>AND p.category_id = #{categoryId}</if> " +
        "<if test='status != null'>AND p.status = #{status}</if> " +
        "<if test='conditionLevel != null'>AND p.condition_level = #{conditionLevel}</if> " +
        "<if test='keyword != null and keyword != \"\"'>AND p.title LIKE CONCAT('%', #{keyword}, '%')</if> " +
        "</script>")
    long countProductsWithFilters(
        @Param("categoryId") Long categoryId,
        @Param("status") Integer status,
        @Param("conditionLevel") Integer conditionLevel,
        @Param("keyword") String keyword);
}