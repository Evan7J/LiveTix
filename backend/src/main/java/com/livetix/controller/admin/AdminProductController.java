package com.livetix.controller.admin;

import com.livetix.common.Result;
import com.livetix.entity.Product;
import com.livetix.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端商品控制器，提供后台商品列表查询、状态修改和删除等管理功能。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    /**
     * 管理端商品列表（全部状态）
     *
     * GET /api/admin/products?page=1&pageSize=10&keyword=手机&status=1
     */
    @GetMapping("/products")
    public Result<?> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort) {
        return productService.listProducts(page, pageSize, categoryId, keyword, sort);
    }

    /**
     * 管理端商品详情
     */
    @GetMapping("/products/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return productService.getProductDetail(id);
    }

    /**
     * 强制修改商品状态（管理员操作，如违规下架）
     *
     * PUT /api/admin/products/{id}/status
     * Body: { "status": 3 }
     */
    @PutMapping("/products/{id}/status")
    public Result<?> updateStatus(@PathVariable Long id, @RequestBody Product body) {
        Product product = productService.getById(id);
        if (product == null) {
            return Result.fail("商品不存在");
        }
        if (body.getStatus() != null) {
            product.setStatus(body.getStatus());
            productService.updateById(product);
        }
        return Result.ok("状态更新成功");
    }

    /**
     * 强制删除商品（管理员操作）
     *
     * DELETE /api/admin/products/{id}
     */
    @DeleteMapping("/products/{id}")
    public Result<?> delete(@PathVariable Long id) {
        productService.removeById(id);
        return Result.ok("删除成功");
    }
}