package com.livetix.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livetix.common.Result;
import com.livetix.common.constant.RedisKey;
import com.livetix.dto.ProductCreateDTO;
import com.livetix.entity.Category;
import com.livetix.entity.Product;
import com.livetix.entity.User;
import com.livetix.mapper.CategoryMapper;
import com.livetix.mapper.ProductMapper;
import com.livetix.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 商品服务实现 — Redis 二级缓存 + 防穿透/击穿/雪崩
 *
 * 设计思路：
 *   1. 商品列表：不走缓存（查询条件组合太多，缓存命中率低），直接走 DB 关联查询
 *   2. 商品详情：走 Redis 缓存（高频访问 + 查询条件单一），TTL 30 分钟
 *   3. 浏览量：Redis 计数器 + 定时任务批量刷回 DB（5 分钟间隔）
 *   4. 缓存穿透防护：不存在的数据缓存空值 __NULL__，TTL 60 秒
 *   5. 缓存击穿防护：SET NX 互斥锁，只有一个线程去查库重建缓存
 *   6. 缓存雪崩防护：TTL 加随机抖动（80%~120%），避免同时过期
 *
 * 面试点：
 *   - 为什么列表不走缓存？因为查询条件多（分类/关键词/排序/分页），组合爆炸，
 *     缓存命中率极低，反而增加 Redis 内存压力和一致性维护成本
 *   - 为什么详情走缓存？商品详情页是最高频的访问入口，且 key 唯一（product:id），
 *     缓存命中率接近 100%，收益远大于成本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final CategoryMapper categoryMapper;

    private final UserMapper userMapper;

    /** 浏览量 Redis Key 前缀 */
    private static final String VIEW_COUNT_KEY = "livetix:product:view:";

    /** 浏览量刷库间隔：5 分钟 */
    private static final long VIEW_FLUSH_INTERVAL_MS = 5 * 60 * 1000;

    /** 空值占位符，防止缓存穿透 */
    private static final String NULL_PLACEHOLDER = "__NULL__";

    /** 空值缓存 TTL：60 秒 */
    private static final long NULL_TTL_SECONDS = 60;

    /** 缓存击穿互斥锁前缀 */
    private static final String MUTEX_KEY = "livetix:mutex:product:";

    /** 互斥锁 TTL：10 秒（防止死锁） */
    private static final long MUTEX_TTL_SECONDS = 10;

    /** 分页最大值 */
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 随机抖动 TTL（80%~120%），防止缓存雪崩
     * 例如基础 TTL=300s，实际可能在 240s~360s 之间
     */
    private long jitteredTtl(long baseSeconds) {
        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        return (long) (baseSeconds * jitter);
    }

    /**
     * 互斥锁重建缓存（防缓存击穿）
     *
     * 流程：
     *   1. 尝试获取互斥锁（SET NX），拿到锁的线程去查库
     *   2. 没拿到锁的线程 sleep 50~100ms 后重试读缓存
     *   3. 如果缓存中已有数据（可能是其他线程重建的），直接返回
     *   4. 如果缓存中仍是空，直接查库兜底
     *
     * @param mutexKey  互斥锁 Key
     * @param cacheKey  缓存 Key
     * @param baseTtl   基础 TTL（秒）
     * @param dbSupplier 数据库查询函数
     * @return 缓存或数据库中的数据
     */
    @SuppressWarnings("unchecked")
    private <T> T rebuildWithMutex(String mutexKey, String cacheKey, long baseTtl,
                                    java.util.function.Supplier<T> dbSupplier) {
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(mutexKey, "1", MUTEX_TTL_SECONDS, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双重检查：拿到锁后再次读缓存，防止重复查库
                T doubleCheck = (T) redisTemplate.opsForValue().get(cacheKey);
                if (doubleCheck != null) {
                    if (NULL_PLACEHOLDER.equals(doubleCheck)) return null;
                    return doubleCheck;
                }
                // 查库
                T value = dbSupplier.get();
                if (value != null) {
                    redisTemplate.opsForValue().set(cacheKey, value, jitteredTtl(baseTtl), TimeUnit.SECONDS);
                } else {
                    // 缓存空值，防穿透
                    redisTemplate.opsForValue().set(cacheKey, NULL_PLACEHOLDER, jitteredTtl(NULL_TTL_SECONDS), TimeUnit.SECONDS);
                }
                return value;
            } catch (Exception e) {
                log.error("重建缓存失败 key={}", cacheKey, e);
                return dbSupplier.get(); // 异常时直接查库兜底
            } finally {
                redisTemplate.delete(mutexKey);
            }
        } else {
            // 没拿到锁，短暂等待后重试读缓存
            try {
                Thread.sleep(50 + ThreadLocalRandom.current().nextLong(50));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            T retry = (T) redisTemplate.opsForValue().get(cacheKey);
            if (retry != null && !NULL_PLACEHOLDER.equals(retry)) return retry;
            return dbSupplier.get(); // 兜底查库
        }
    }

    // ==================== 商品列表（公开接口） ====================

    @Override
    public Result<?> listProducts(Integer page, Integer pageSize, Long categoryId,
                                   String keyword, String sort) {
        // 安全校验：分页上限
        if (pageSize > MAX_PAGE_SIZE) pageSize = MAX_PAGE_SIZE;
        if (page < 1) page = 1;

        int offset = (page - 1) * pageSize;

        // 列表查询条件多，不走缓存，直接查 DB（带关联查询）
        List<Product> records = baseMapper.selectProductsWithFilters(
                categoryId, 1, null, keyword, sort, offset, pageSize);
        long total = baseMapper.countProductsWithFilters(categoryId, 1, null, keyword);

        Page<Product> result = new Page<>(page, pageSize, total);
        result.setRecords(records);
        return Result.ok(result);
    }

    // ==================== 商品详情（Redis 缓存） ====================

    @Override
    public Result<?> getProductDetail(Long productId) {
        String cacheKey = RedisKey.PRODUCT_DETAIL + productId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        // 命中空值缓存 → 商品不存在
        if (NULL_PLACEHOLDER.equals(cached)) {
            return Result.fail("商品不存在");
        }
        // 命中正常缓存 → 直接返回，异步增加浏览量
        if (cached != null) {
            incrementViewCount(productId);
            return Result.ok(cached);
        }

        // 缓存未命中 → 加互斥锁查库
        String mutexKey = MUTEX_KEY + cacheKey;
        Product product = rebuildWithMutex(mutexKey, cacheKey, RedisKey.CACHE_TTL_30M, () -> {
            Product p = this.getById(productId);
            if (p == null) return null;

            // 关联查询分类名和卖家信息
            if (p.getCategoryId() != null) {
                Category category = categoryMapper.selectById(p.getCategoryId());
                if (category != null) p.setCategoryName(category.getName());
            }
            if (p.getUserId() != null) {
                User seller = userMapper.selectById(p.getUserId());
                if (seller != null) {
                    p.setSellerNickname(seller.getNickname());
                    p.setSellerAvatar(seller.getAvatar());
                }
            }
            return p;
        });

        if (product == null) {
            return Result.fail("商品不存在");
        }

        // 增加浏览量
        incrementViewCount(productId);
        return Result.ok(product);
    }

    /**
     * Redis 原子自增浏览量
     * 首次递增时设置 24 小时过期，防止冷数据占用 Redis 内存
     */
    private Long incrementViewCount(Long productId) {
        try {
            String key = VIEW_COUNT_KEY + productId;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, 24, TimeUnit.HOURS);
            }
            return count;
        } catch (Exception e) {
            log.warn("Redis 浏览量自增失败 productId={}", productId, e);
            return 0L;
        }
    }

    // ==================== 发布商品 ====================

    @Override
    public Result<?> createProduct(Long userId, ProductCreateDTO dto) {
        // DTO → Entity 转换（只复制允许的字段，防止注入敏感字段）
        Product product = new Product();
        product.setTitle(dto.getTitle());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setOriginalPrice(dto.getOriginalPrice());
        product.setCategoryId(dto.getCategoryId());
        product.setImages(dto.getImages());
        product.setCoverImage(dto.getCoverImage());
        product.setConditionLevel(dto.getConditionLevel() != null ? dto.getConditionLevel() : 1);
        product.setTradeLocation(dto.getTradeLocation());
        product.setIsNegotiable(dto.getIsNegotiable() != null ? dto.getIsNegotiable() : 0);

        // 后端强制设置，不信任客户端
        product.setUserId(userId);
        product.setStatus(1);      // 默认在售
        product.setViewCount(0);
        product.setFavoriteCount(0);

        this.save(product);
        return Result.ok("发布成功", product);
    }

    // ==================== 编辑商品 ====================

    @Override
    public Result<?> updateProduct(Long userId, Long productId, ProductCreateDTO dto) {
        Product product = this.getById(productId);
        if (product == null) {
            return Result.fail("商品不存在");
        }
        // 归属权校验：防止越权修改他人商品
        if (!product.getUserId().equals(userId)) {
            return Result.fail("无权操作此商品");
        }

        // 只更新允许修改的字段
        product.setTitle(dto.getTitle());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setOriginalPrice(dto.getOriginalPrice());
        product.setCategoryId(dto.getCategoryId());
        product.setImages(dto.getImages());
        product.setCoverImage(dto.getCoverImage());
        product.setConditionLevel(dto.getConditionLevel());
        product.setTradeLocation(dto.getTradeLocation());
        product.setIsNegotiable(dto.getIsNegotiable());

        this.updateById(product);

        // 清除该商品缓存，下次访问时重建
        clearProductCache(productId);
        return Result.ok("更新成功");
    }

    // ==================== 下架商品 ====================

    @Override
    public Result<?> offShelf(Long userId, Long productId) {
        Product product = this.getById(productId);
        if (product == null) {
            return Result.fail("商品不存在");
        }
        if (!product.getUserId().equals(userId)) {
            return Result.fail("无权操作此商品");
        }
        if (product.getStatus() != 1) {
            return Result.fail("商品不在在售状态");
        }

        product.setStatus(3); // 下架
        this.updateById(product);
        clearProductCache(productId);
        return Result.ok("已下架");
    }

    // ==================== 上架商品 ====================

    @Override
    public Result<?> onShelf(Long userId, Long productId) {
        Product product = this.getById(productId);
        if (product == null) {
            return Result.fail("商品不存在");
        }
        if (!product.getUserId().equals(userId)) {
            return Result.fail("无权操作此商品");
        }
        if (product.getStatus() != 3) {
            return Result.fail("商品不在下架状态");
        }

        product.setStatus(1); // 重新上架
        this.updateById(product);
        clearProductCache(productId);
        return Result.ok("已上架");
    }

    // ==================== 删除商品 ====================

    @Override
    public Result<?> deleteProduct(Long userId, Long productId) {
        Product product = this.getById(productId);
        if (product == null) {
            return Result.fail("商品不存在");
        }
        if (!product.getUserId().equals(userId)) {
            return Result.fail("无权操作此商品");
        }

        this.removeById(productId); // MyBatis-Plus 逻辑删除
        clearProductCache(productId);
        return Result.ok("删除成功");
    }

    // ==================== 我的商品列表 ====================

    @Override
    public Result<?> getMyProducts(Long userId, Integer page, Integer pageSize, Integer status) {
        if (pageSize > MAX_PAGE_SIZE) pageSize = MAX_PAGE_SIZE;
        if (page < 1) page = 1;

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getUserId, userId)
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getCreateTime);

        Page<Product> result = this.page(new Page<>(page, pageSize), wrapper);
        return Result.ok(result);
    }

    // ==================== 浏览量定时刷库 ====================

    @Override
    @Scheduled(fixedDelay = VIEW_FLUSH_INTERVAL_MS)
    public void flushViewCountsToDB() {
        try {
            String pattern = VIEW_COUNT_KEY + "*";
            List<Map<String, Object>> batchItems = new ArrayList<>();

            // 使用 SCAN 命令避免 KEYS 阻塞 Redis
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
            try {
                Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                        .getConnection().scan(options);
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    try {
                        String productIdStr = key.substring(VIEW_COUNT_KEY.length());
                        Long productId = Long.parseLong(productIdStr);
                        Object countObj = redisTemplate.opsForValue().getAndDelete(key);
                        if (countObj == null) continue;

                        long increment = 0L;
                        if (countObj instanceof Number) increment = ((Number) countObj).longValue();

                        if (increment > 0) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("id", productId);
                            item.put("count", (int) increment);
                            batchItems.add(item);
                        }
                    } catch (Exception e) {
                        log.error("收集浏览量失败 key={}", key, e);
                    }
                }
                cursor.close();
            } catch (Exception e) {
                // SCAN 失败时降级使用 KEYS
                log.warn("SCAN 失败，降级使用 KEYS", e);
                Set<String> keys = redisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    for (String key : keys) {
                        try {
                            String productIdStr = key.substring(VIEW_COUNT_KEY.length());
                            Long productId = Long.parseLong(productIdStr);
                            Object countObj = redisTemplate.opsForValue().getAndDelete(key);
                            if (countObj == null) continue;
                            long increment = 0L;
                            if (countObj instanceof Number) increment = ((Number) countObj).longValue();
                            if (increment > 0) {
                                Map<String, Object> item = new HashMap<>();
                                item.put("id", productId);
                                item.put("count", (int) increment);
                                batchItems.add(item);
                            }
                        } catch (Exception ex) {
                            log.error("收集浏览量失败 key={}", key, ex);
                        }
                    }
                }
            }

            if (!batchItems.isEmpty()) {
                baseMapper.batchIncrementViewCount(batchItems);
                log.info("批量刷回 {} 个商品浏览量到 DB", batchItems.size());
            }
        } catch (Exception e) {
            log.error("浏览量定时刷库任务失败", e);
        }
    }

    // ==================== 缓存清理 ====================

    /**
     * 清除商品相关缓存（编辑/下架/上架/删除时调用）
     */
    private void clearProductCache(Long productId) {
        redisTemplate.delete(RedisKey.PRODUCT_DETAIL + productId);
    }
}