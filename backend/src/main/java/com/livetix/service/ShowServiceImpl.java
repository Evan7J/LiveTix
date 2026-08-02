package com.livetix.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livetix.common.Result;
import com.livetix.common.constant.RedisKey;
import com.livetix.entity.Category;
import com.livetix.entity.Show;
import com.livetix.entity.Venue;
import com.livetix.mapper.CategoryMapper;
import com.livetix.mapper.ShowMapper;
import com.livetix.mapper.VenueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 演出服务实现 — 二级缓存（Redis + MySQL）
 * 缓存穿透/击穿/雪崩都有防护
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShowServiceImpl extends ServiceImpl<ShowMapper, Show> implements ShowService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final VenueMapper venueMapper;

    private final CategoryMapper categoryMapper;

    private static final String VIEW_COUNT_KEY = "livetix:show:view:";
    private static final long VIEW_FLUSH_INTERVAL_MS = 5 * 60 * 1000;
    private static final String NULL_PLACEHOLDER = "__NULL__";
    private static final long NULL_TTL_SECONDS = 60;
    private static final String MUTEX_KEY = "livetix:mutex:";
    private static final long MUTEX_TTL_SECONDS = 10;

    private long jitteredTtl(long baseSeconds) {
        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        return (long) (baseSeconds * jitter);
    }

    @SuppressWarnings("unchecked")
    private <T> T rebuildWithMutex(String mutexKey, String cacheKey, long baseTtl,
                                    java.util.function.Supplier<T> dbSupplier) {
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(mutexKey, "1", MUTEX_TTL_SECONDS, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(locked)) {
            try {
                T doubleCheck = (T) redisTemplate.opsForValue().get(cacheKey);
                if (doubleCheck != null) {
                    if (NULL_PLACEHOLDER.equals(doubleCheck)) return null;
                    return doubleCheck;
                }
                T value = dbSupplier.get();
                if (value != null) {
                    redisTemplate.opsForValue().set(cacheKey, value, jitteredTtl(baseTtl), TimeUnit.SECONDS);
                } else {
                    redisTemplate.opsForValue().set(cacheKey, NULL_PLACEHOLDER, jitteredTtl(NULL_TTL_SECONDS), TimeUnit.SECONDS);
                }
                return value;
            } catch (Exception e) {
                log.error("Failed to rebuild cache for key: {}", cacheKey, e);
                return dbSupplier.get();
            } finally {
                redisTemplate.delete(mutexKey);
            }
        } else {
            try {
                Thread.sleep(50 + ThreadLocalRandom.current().nextLong(50));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            T retry = (T) redisTemplate.opsForValue().get(cacheKey);
            if (retry != null && !NULL_PLACEHOLDER.equals(retry)) return retry;
            return dbSupplier.get();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result<?> getHotShows() {
        Object redisCached = redisTemplate.opsForValue().get(RedisKey.HOT_SHOWS);
        if (redisCached != null) {
            return Result.ok(redisCached);
        }

        String cacheKey = RedisKey.HOT_SHOWS;
        String mutexKey = MUTEX_KEY + cacheKey;
        List<Show> result = rebuildWithMutex(mutexKey, cacheKey, RedisKey.CACHE_TTL_5M, () -> {
            return this.list(new LambdaQueryWrapper<Show>()
                    .eq(Show::getIsHot, 1)
                    .eq(Show::getStatus, 1)
                    .orderByDesc(Show::getSort)
                    .last("LIMIT 8"));
        });

        return Result.ok(result != null ? result : List.of());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result<?> getShowsByCategory(Long categoryId, Integer page, Integer pageSize) {
        String cacheKey = RedisKey.SHOW_CATEGORY + categoryId + ":" + page + ":" + pageSize;

        Object redisCached = redisTemplate.opsForValue().get(cacheKey);
        if (redisCached != null) {
            return Result.ok(redisCached);
        }

        String mutexKey = MUTEX_KEY + cacheKey;
        Page<Show> result = rebuildWithMutex(mutexKey, cacheKey, RedisKey.CACHE_TTL_5M, () -> {
            LambdaQueryWrapper<Show> wrapper = new LambdaQueryWrapper<Show>()
                    .eq(Show::getCategoryId, categoryId)
                    .eq(Show::getStatus, 1)
                    .orderByDesc(Show::getSort)
                    .orderByDesc(Show::getShowTime);
            return this.page(new Page<>(page, pageSize), wrapper);
        });

        return Result.ok(result != null ? result : new Page<>(page, pageSize));
    }

    @Override
    public Result<?> getShowDetail(Long showId) {
        String cacheKey = RedisKey.SHOW_DETAIL + showId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        if (NULL_PLACEHOLDER.equals(cached)) {
            return Result.fail("演出不存在");
        }
        if (cached != null) {
            incrementViewCount(showId);
            return Result.ok(cached);
        }

        String mutexKey = MUTEX_KEY + cacheKey;
        Show show = rebuildWithMutex(mutexKey, cacheKey, RedisKey.CACHE_TTL_30M, () -> {
            Show s = this.getById(showId);
            if (s == null) return null;

            if (s.getVenueId() != null) {
                Venue venue = venueMapper.selectById(s.getVenueId());
                if (venue != null) {
                    s.setVenueName(venue.getName());
                    s.setVenueCity(venue.getCity());
                }
            }
            if (s.getCategoryId() != null) {
                Category category = categoryMapper.selectById(s.getCategoryId());
                if (category != null) s.setCategoryName(category.getName());
            }

            Long viewCount = incrementViewCount(showId);
            s.setViewCount(viewCount != null ? viewCount.intValue() : s.getViewCount() + 1);
            return s;
        });

        if (show == null) {
            return Result.fail("演出不存在");
        }

        return Result.ok(show);
    }

    private Long incrementViewCount(Long showId) {
        try {
            String key = VIEW_COUNT_KEY + showId;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, 24, TimeUnit.HOURS);
            }
            return count;
        } catch (Exception e) {
            log.warn("Redis view count increment failed for show={}", showId, e);
            Show show = this.getById(showId);
            if (show != null) {
                show.setViewCount(show.getViewCount() + 1);
                this.updateById(show);
                return (long) show.getViewCount();
            }
            return 0L;
        }
    }

    @Override
    @Scheduled(fixedDelay = VIEW_FLUSH_INTERVAL_MS)
    public void flushViewCountsToDB() {
        try {
            String pattern = VIEW_COUNT_KEY + "*";
            List<Map<String, Object>> batchItems = new ArrayList<>();

            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
            try {
                Cursor<byte[]> cursor = redisTemplate.getConnectionFactory().getConnection().scan(options);
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    try {
                        String showIdStr = key.substring(VIEW_COUNT_KEY.length());
                        Long showId = Long.parseLong(showIdStr);
                        Object countObj = redisTemplate.opsForValue().getAndDelete(key);
                        if (countObj == null) continue;

                        Long increment = 0L;
                        if (countObj instanceof Number) increment = ((Number) countObj).longValue();

                        if (increment > 0) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("id", showId);
                            item.put("count", increment.intValue());
                            batchItems.add(item);
                        }
                    } catch (Exception e) {
                        log.error("Failed to collect view count for key: {}", key, e);
                    }
                }
                cursor.close();
            } catch (Exception e) {
                log.warn("SCAN failed, falling back to KEYS", e);
                Set<String> keys = redisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    for (String key : keys) {
                        try {
                            String showIdStr = key.substring(VIEW_COUNT_KEY.length());
                            Long showId = Long.parseLong(showIdStr);
                            Object countObj = redisTemplate.opsForValue().getAndDelete(key);
                            if (countObj == null) continue;
                            Long increment = 0L;
                            if (countObj instanceof Number) increment = ((Number) countObj).longValue();
                            if (increment > 0) {
                                Map<String, Object> item = new HashMap<>();
                                item.put("id", showId);
                                item.put("count", increment.intValue());
                                batchItems.add(item);
                            }
                        } catch (Exception ex) {
                            log.error("Failed to collect view count for key: {}", key, ex);
                        }
                    }
                }
            }

            if (!batchItems.isEmpty()) {
                baseMapper.batchIncrementViewCount(batchItems);
                log.info("Batch flushed {} show view counts to DB", batchItems.size());
            }
        } catch (Exception e) {
            log.error("View count flush task failed", e);
        }
    }

    @Override
    public Result<?> searchShows(String keyword, Integer page, Integer pageSize, Long categoryId) {
        LambdaQueryWrapper<Show> wrapper = new LambdaQueryWrapper<Show>()
                .and(w -> w.like(Show::getTitle, keyword).or().like(Show::getArtists, keyword))
                .eq(Show::getStatus, 1)
                .eq(categoryId != null, Show::getCategoryId, categoryId)
                .orderByDesc(Show::getShowTime);
        Page<Show> result = this.page(new Page<>(page, pageSize), wrapper);
        return Result.ok(result);
    }

    @Override
    public Result<?> listShows(Integer page, Integer pageSize, Long categoryId,
                               Integer status, String keyword,
                               String city, String timeRange, String date, String sort) {
        int offset = (page - 1) * pageSize;
        boolean needAdvancedFilter = (city != null && !city.isBlank())
                || (timeRange != null && !timeRange.isBlank());

        if (needAdvancedFilter) {
            List<Show> records = baseMapper.selectShowsWithFilters(
                    categoryId, city, timeRange, date, keyword, sort, offset, pageSize);
            long total = baseMapper.countShowsWithFilters(categoryId, city, timeRange, date, keyword);
            var result = new Page<Show>(page, pageSize, total);
            result.setRecords(records);
            return Result.ok(result);
        }

        LambdaQueryWrapper<Show> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) wrapper.eq(Show::getCategoryId, categoryId);
        if (status != null) wrapper.eq(Show::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Show::getTitle, keyword).or().like(Show::getArtists, keyword));
        }
        if ("soonest".equals(sort)) {
            wrapper.orderByAsc(Show::getShowTime);
        } else if ("latest".equals(sort)) {
            wrapper.orderByDesc(Show::getCreateTime);
        } else {
            wrapper.orderByDesc(Show::getSort).orderByDesc(Show::getIsHot).orderByDesc(Show::getCreateTime);
        }
        Page<Show> result = this.page(new Page<>(page, pageSize), wrapper);

        if (!result.getRecords().isEmpty()) {
            var venueIds = result.getRecords().stream().map(Show::getVenueId)
                    .filter(id -> id != null).distinct().toList();
            var categoryIds = result.getRecords().stream().map(Show::getCategoryId)
                    .filter(id -> id != null).distinct().toList();

            java.util.Map<Long, Venue> venueMap = new java.util.HashMap<>();
            java.util.Map<Long, Category> categoryMap = new java.util.HashMap<>();

            if (!venueIds.isEmpty()) {
                venueMapper.selectBatchIds(venueIds).forEach(v -> venueMap.put(v.getId(), v));
            }
            if (!categoryIds.isEmpty()) {
                categoryMapper.selectBatchIds(categoryIds).forEach(c -> categoryMap.put(c.getId(), c));
            }

            for (Show show : result.getRecords()) {
                if (show.getVenueId() != null && show.getVenueName() == null) {
                    Venue venue = venueMap.get(show.getVenueId());
                    if (venue != null) { show.setVenueName(venue.getName()); show.setVenueCity(venue.getCity()); }
                }
                if (show.getCategoryId() != null && show.getCategoryName() == null) {
                    Category category = categoryMap.get(show.getCategoryId());
                    if (category != null) show.setCategoryName(category.getName());
                }
            }
        }
        return Result.ok(result);
    }

    @Override
    public Result<?> saveOrUpdateShow(Show show) {
        if (show.getShowStatus() != null) {
            switch (show.getShowStatus()) {
                case "upcoming": case "presale": if (show.getStatus() == null || show.getStatus() != 0) show.setStatus(0); break;
                case "onsale": if (show.getStatus() == null || show.getStatus() != 1) show.setStatus(1); break;
                case "soldout": if (show.getStatus() == null || show.getStatus() != 2) show.setStatus(2); break;
                case "ended": if (show.getStatus() == null || show.getStatus() != 3) show.setStatus(3); break;
            }
        }
        if (show.getStatus() != null && show.getShowStatus() == null) {
            switch (show.getStatus()) {
                case 0: show.setShowStatus("upcoming"); break;
                case 1: show.setShowStatus("onsale"); break;
                case 2: show.setShowStatus("soldout"); break;
                case 3: case 4: show.setShowStatus("ended"); break;
            }
        }
        if (show.getAvailableStock() != null) {
            if (show.getAvailableStock() <= 0 && show.getStatus() != null && show.getStatus() == 1) {
                show.setStatus(2); show.setShowStatus("soldout");
            } else if (show.getAvailableStock() > 0 && show.getStatus() != null && show.getStatus() == 2) {
                show.setStatus(1); show.setShowStatus("onsale");
            }
        }

        this.saveOrUpdate(show);
        clearShowCache(show.getId());

        return Result.ok("保存成功");
    }

    private void clearShowCache(Long showId) {
        redisTemplate.delete(RedisKey.HOT_SHOWS);
        redisTemplate.delete(RedisKey.SHOW_DETAIL + showId);
        redisTemplate.delete(RedisKey.SHOW_STOCK + showId);
        try {
            ScanOptions options = ScanOptions.scanOptions().match(RedisKey.SHOW_CATEGORY + "*").count(100).build();
            var conn = redisTemplate.getConnectionFactory().getConnection();
            Cursor<byte[]> cursor = conn.scan(options);
            while (cursor.hasNext()) {
                String key = new String(cursor.next(), StandardCharsets.UTF_8);
                redisTemplate.delete(key);
            }
            cursor.close();
        } catch (Exception e) {
            var keys = redisTemplate.keys(RedisKey.SHOW_CATEGORY + "*");
            if (keys != null && !keys.isEmpty()) {
                keys.forEach(redisTemplate::delete);
            }
        }
    }
}