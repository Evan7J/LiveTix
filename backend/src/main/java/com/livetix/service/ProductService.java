package com.livetix.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livetix.common.Result;
import com.livetix.dto.ProductCreateDTO;
import com.livetix.entity.Product;

/**
 * 商品服务接口 — 校园闲置交易核心业务
 *
 * 业务能力：
 *   1. 商品列表查询（分页 + 分类筛选 + 关键词搜索 + 排序）
 *   2. 商品详情查看（含浏览量异步计数 + Redis 缓存防穿透/击穿）
 *   3. 发布商品（DTO 隔离 + 用户身份校验）
 *   4. 编辑/下架/上架商品（归属权校验，防止越权操作）
 *   5. 浏览量定时批量刷回数据库
 */
public interface ProductService extends IService<Product> {

    /**
     * 分页查询商品列表（公开接口）
     *
     * @param page       页码，从1开始
     * @param pageSize   每页条数
     * @param categoryId 分类筛选（可选）
     * @param keyword    关键词搜索（可选，匹配标题）
     * @param sort       排序方式：newest（最新）price_asc（价格升序）price_desc（价格降序）
     */
    Result<?> listProducts(Integer page, Integer pageSize, Long categoryId,
                           String keyword, String sort);

    /**
     * 查询商品详情（含浏览量 +1 和 Redis 缓存）
     *
     * 缓存策略：
     *   - 命中缓存直接返回，同时异步增加浏览量
     *   - 缓存未命中时加互斥锁查库，防止缓存击穿
     *   - 查不到数据时缓存空值占位符 __NULL__，防止缓存穿透
     */
    Result<?> getProductDetail(Long productId);

    /**
     * 发布商品（需登录）
     *
     * @param userId 当前登录用户ID（从 Sa-Token 获取）
     * @param dto    商品创建请求体
     */
    Result<?> createProduct(Long userId, ProductCreateDTO dto);

    /**
     * 编辑商品（需登录 + 归属权校验）
     *
     * @param userId    当前登录用户ID
     * @param productId 商品ID
     * @param dto       编辑请求体
     */
    Result<?> updateProduct(Long userId, Long productId, ProductCreateDTO dto);

    /**
     * 下架商品（卖家操作）
     *
     * @param userId    当前登录用户ID
     * @param productId 商品ID
     */
    Result<?> offShelf(Long userId, Long productId);

    /**
     * 重新上架商品（卖家操作）
     *
     * @param userId    当前登录用户ID
     * @param productId 商品ID
     */
    Result<?> onShelf(Long userId, Long productId);

    /**
     * 删除商品（逻辑删除，卖家操作）
     *
     * @param userId    当前登录用户ID
     * @param productId 商品ID
     */
    Result<?> deleteProduct(Long userId, Long productId);

    /**
     * 查询当前用户发布的商品列表
     *
     * @param userId   当前登录用户ID
     * @param page     页码
     * @param pageSize 每页条数
     * @param status   状态筛选（可选）：1在售 2已售 3下架
     */
    Result<?> getMyProducts(Long userId, Integer page, Integer pageSize, Integer status);

    /**
     * 定时任务：将 Redis 中的浏览量增量批量刷回数据库
     * 每5分钟执行一次，使用 SCAN 避免阻塞 Redis
     */
    void flushViewCountsToDB();
}