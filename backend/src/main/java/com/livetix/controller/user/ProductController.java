package com.livetix.controller.user;

import cn.dev33.satoken.stp.StpUtil;
import com.livetix.common.Result;
import com.livetix.dto.ProductCreateDTO;
import com.livetix.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端商品控制器 — 校园闲置交易（需登录）
 *
 * 公开接口（商品列表、商品详情）在 PublicController 中，路径为 /api/public/products
 * 本控制器所有接口需要登录后才能访问
 *
 * 安全设计：
 *   1. 所有写操作从 Sa-Token 获取当前用户ID，不信任客户端传入的 userId
 *   2. 编辑/下架/上架/删除操作均校验商品归属权
 *   3. 发布时间使用 DTO 隔离，防止客户端注入敏感字段
 */
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ==================== 需登录接口 ====================

    /**
     * 发布商品
     *
     * POST /api/product
     *
     * 安全措施：
     *   - 使用 @Valid 校验必填字段
     *   - 后端从 Sa-Token 获取用户ID，不信任客户端
     *   - 强制设置 status=1（在售），防止客户端注入 status=2（已售）绕过交易
     */
    @PostMapping
    public Result<?> create(@Valid @RequestBody ProductCreateDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        return productService.createProduct(userId, dto);
    }

    /**
     * 编辑商品
     *
     * PUT /api/product/{id}
     *
     * 安全措施：校验商品归属权（只能编辑自己的商品）
     */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody ProductCreateDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        return productService.updateProduct(userId, id, dto);
    }

    /**
     * 下架商品（卖家主动下架）
     *
     * PUT /api/product/{id}/off-shelf
     */
    @PutMapping("/{id}/off-shelf")
    public Result<?> offShelf(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        return productService.offShelf(userId, id);
    }

    /**
     * 重新上架商品
     *
     * PUT /api/product/{id}/on-shelf
     */
    @PutMapping("/{id}/on-shelf")
    public Result<?> onShelf(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        return productService.onShelf(userId, id);
    }

    /**
     * 删除商品（逻辑删除）
     *
     * DELETE /api/product/{id}
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        return productService.deleteProduct(userId, id);
    }

    /**
     * 我的商品列表
     *
     * GET /api/product/my?page=1&pageSize=10&status=1
     *
     * @param status 状态筛选（可选）：1在售 2已售 3下架
     */
    @GetMapping("/my")
    public Result<?> myProducts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        long userId = StpUtil.getLoginIdAsLong();
        return productService.getMyProducts(userId, page, pageSize, status);
    }
}