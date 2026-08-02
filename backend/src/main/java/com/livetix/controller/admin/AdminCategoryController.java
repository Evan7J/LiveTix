package com.livetix.controller.admin;

import com.livetix.common.Result;
import com.livetix.entity.Category;
import com.livetix.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryMapper categoryMapper;

    @GetMapping("/categories")
    public Result<?> list() {
        var list = categoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSort));
        return Result.ok(list);
    }

    @PostMapping("/categories")
    public Result<?> create(@RequestBody Category category) {
        categoryMapper.insert(category);
        return Result.ok("创建成功", category);
    }

    @PutMapping("/categories/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Category category) {
        category.setId(id);
        categoryMapper.updateById(category);
        return Result.ok("更新成功");
    }

    @DeleteMapping("/categories/{id}")
    public Result<?> delete(@PathVariable Long id) {
        categoryMapper.deleteById(id);
        return Result.ok("删除成功");
    }

    /**
     * 26 修复: 一条 CASE WHEN SQL 替代 N 次 selectById + updateById
     */
    @PutMapping("/categories/sort")
    public Result<?> sort(@RequestBody List<Map<String, Object>> items) {
        if (items != null && !items.isEmpty()) {
            // 确保 Map 中的 id/sort 有正确的 key
            for (var item : items) {
                if (item.get("id") instanceof Integer i) item.put("id", i.longValue());
            }
            categoryMapper.batchUpdateSort(items);
        }
        return Result.ok("排序更新成功");
    }
}
