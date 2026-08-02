package com.livetix.controller.admin;

import com.livetix.common.Result;
import com.livetix.entity.Show;
import com.livetix.service.ShowService;
import com.livetix.service.StockPreloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin: Show/Performance management
 */
@RestController
@RequestMapping("/api/admin/shows")
@RequiredArgsConstructor
public class AdminShowController {

    private final ShowService showService;

    private final StockPreloadService stockPreloadService;

    @GetMapping
    public Result<?> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return showService.listShows(page, pageSize, categoryId, status, keyword, null, null, null, null);
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return showService.getShowDetail(id);
    }

    @PostMapping
    public Result<?> save(@RequestBody Show show) {
        return showService.saveOrUpdateShow(show);
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Show show) {
        show.setId(id);
        return showService.saveOrUpdateShow(show);
    }

    /**
     * 逻辑删除后清除 Redis 缓存
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        showService.removeById(id);
        // 27: 通过 saveOrUpdateShow 触发 clearShowCache（会清理该 ID 相关的缓存 key）
        com.livetix.entity.Show stub = new com.livetix.entity.Show();
        stub.setId(id);
        stub.setDeleted(1);
        showService.saveOrUpdateShow(stub);
        return Result.ok("删除成功");
    }

    // ==================== P1-5: 秒杀库存预热 ====================

    /**
     * 秒杀前预热单场演出库存到 Redis
     * POST /api/admin/shows/{id}/preload-stock
     */
    @PostMapping("/{id}/preload-stock")
    public Result<?> preloadStock(@PathVariable Long id) {
        try {
            int stock = stockPreloadService.preloadStock(id);
            return Result.ok("库存预热成功", Map.of("showId", id, "stock", stock));
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 批量预热所有在售演出库存 */
    @PostMapping("/preload-stock/all")
    public Result<?> preloadAllStock() {
        int count = stockPreloadService.preloadAllOnSale();
        return Result.ok("库存预热完成", Map.of("preloadedShows", count));
    }

    /** 查看某演出当前 Redis 预热库存（-1 表示未预热） */
    @GetMapping("/{id}/preload-stock")
    public Result<?> getPreloadStock(@PathVariable Long id) {
        return Result.ok(Map.of("showId", id, "redisStock", stockPreloadService.getPreloadStock(id)));
    }

    /** 活动结束后清除 Redis 预热库存 */
    @DeleteMapping("/{id}/preload-stock")
    public Result<?> clearPreloadStock(@PathVariable Long id) {
        stockPreloadService.clearPreloadStock(id);
        return Result.ok("已清除预热库存");
    }
}
