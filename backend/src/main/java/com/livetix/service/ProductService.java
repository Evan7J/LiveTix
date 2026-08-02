package com.livetix.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livetix.common.Result;
import com.livetix.dto.ProductCreateDTO;
import com.livetix.entity.Product;

/**
 * 商品服务接口，提供商品列表、详情、发布、编辑、上下架等功能。
 */
public interface ProductService extends IService<Product> {

    /**
     * 分页查询商品列表（公开接口）
     */
    Result<?> listProducts(Integer page, Integer pageSize, Long categoryId,
                           String keyword, String sort);

    /**
     * 查询商品详情，含浏览量计数和缓存
     */
    Result<?> getProductDetail(Long productId);

    /**
     * 发布商品（需登录）
     */
    Result<?> createProduct(Long userId, ProductCreateDTO dto);

    /**
     * 编辑商品（需登录 + 归属权校验）
     */
    Result<?> updateProduct(Long userId, Long productId, ProductCreateDTO dto);

    /**
     * 下架商品（卖家操作）
     */
    Result<?> offShelf(Long userId, Long productId);

    /**
     * 重新上架商品（卖家操作）
     */
    Result<?> onShelf(Long userId, Long productId);

    /**
     * 删除商品（逻辑删除，卖家操作）
     */
    Result<?> deleteProduct(Long userId, Long productId);

    /**
     * 查询当前用户发布的商品列表
     */
    Result<?> getMyProducts(Long userId, Integer page, Integer pageSize, Integer status);

    /**
     * 定时将 Redis 浏览量增量刷回数据库，每 5 分钟执行一次
     */
    void flushViewCountsToDB();
}