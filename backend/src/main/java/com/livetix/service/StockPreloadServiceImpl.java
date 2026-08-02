package com.livetix.service;

import com.livetix.entity.Show;
import com.livetix.mapper.ShowMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 库存预热服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPreloadServiceImpl implements StockPreloadService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final ShowMapper showMapper;

    private static final String STOCK_PRE_KEY = "livetix:stock:pre:";
    private static final long STOCK_PRE_TTL = 86400;

    private DefaultRedisScript<Long> stockRestoreScript;

    @PostConstruct
    public void init() {
        stockRestoreScript = new DefaultRedisScript<>();
        stockRestoreScript.setLocation(new ClassPathResource("scripts/stock_restore.lua"));
        stockRestoreScript.setResultType(Long.class);
    }

    @Override
    public int preloadStock(Long showId) {
        Show show = showMapper.selectById(showId);
        if (show == null) {
            throw new RuntimeException("演出不存在");
        }
        if (show.getAvailableStock() == null || show.getAvailableStock() <= 0) {
            throw new RuntimeException("该演出无可用库存");
        }

        String key = STOCK_PRE_KEY + showId;
        int stock = show.getAvailableStock();
        redisTemplate.opsForValue().set(key, String.valueOf(stock), STOCK_PRE_TTL, TimeUnit.SECONDS);

        log.info("Stock preloaded: showId={}, stock={}, ttl={}s", showId, stock, STOCK_PRE_TTL);
        return stock;
    }

    @Override
    public int preloadAllOnSale() {
        var shows = showMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Show>()
                        .eq(Show::getStatus, 1)
                        .gt(Show::getAvailableStock, 0));

        int count = 0;
        for (Show show : shows) {
            try {
                preloadStock(show.getId());
                count++;
            } catch (Exception e) {
                log.error("Failed to preload stock for showId={}", show.getId(), e);
            }
        }
        log.info("Batch preloaded {} shows' stock to Redis", count);
        return count;
    }

    @Override
    public int getPreloadStock(Long showId) {
        Object val = redisTemplate.opsForValue().get(STOCK_PRE_KEY + showId);
        if (val == null) return -1;
        return Integer.parseInt(val.toString());
    }

    @Override
    public void clearPreloadStock(Long showId) {
        redisTemplate.delete(STOCK_PRE_KEY + showId);
        log.info("Stock preload cleared: showId={}", showId);
    }

    @Override
    public long restorePreloadStock(Long showId, int quantity) {
        if (showId == null || quantity <= 0) return -1;
        try {
            Long result = redisTemplate.execute(stockRestoreScript,
                    Collections.singletonList(STOCK_PRE_KEY + showId), quantity);
            long restored = result != null ? result : -1;
            if (restored >= 0) {
                log.info("Redis pre-stock restored: showId={}, qty={}, now={}", showId, quantity, restored);
            }
            return restored;
        } catch (Exception e) {
            log.error("Failed to restore Redis pre-stock: showId={}, qty={}", showId, quantity, e);
            return -1;
        }
    }

    @Override
    @Scheduled(fixedDelay = 30000)
    public void calibrateStock() {
        List<Show> shows;
        try {
            shows = showMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Show>()
                            .in(Show::getStatus, 1, 2));
        } catch (Exception e) {
            log.error("Stock calibration: failed to load shows", e);
            return;
        }

        for (Show show : shows) {
            try {
                String key = STOCK_PRE_KEY + show.getId();
                Object val = redisTemplate.opsForValue().get(key);
                if (val == null) continue;

                int redisStock = Integer.parseInt(val.toString());
                int dbStock = show.getAvailableStock() != null ? show.getAvailableStock() : 0;
                if (redisStock > dbStock) {
                    redisTemplate.opsForValue().set(key, String.valueOf(dbStock),
                            STOCK_PRE_TTL, TimeUnit.SECONDS);
                    log.warn("Stock calibrated: showId={}, redis {} -> db {}",
                            show.getId(), redisStock, dbStock);
                }
            } catch (Exception e) {
                log.error("Stock calibration failed for showId={}", show.getId(), e);
            }
        }
    }
}