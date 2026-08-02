package com.livetix.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.livetix.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 26: 批量更新排序 — 单条 CASE WHEN SQL 替代 N 次 selectById + updateById
     */
    @Update("<script>" +
        "UPDATE t_category SET sort = CASE id " +
        "<foreach collection='items' item='item'>" +
        "WHEN #{item.id} THEN #{item.sort} " +
        "</foreach>" +
        "END WHERE id IN " +
        "<foreach collection='items' item='item' open='(' separator=',' close=')'>" +
        "#{item.id}" +
        "</foreach>" +
        "</script>")
    void batchUpdateSort(@Param("items") java.util.List<Map<String, Object>> items);
}
