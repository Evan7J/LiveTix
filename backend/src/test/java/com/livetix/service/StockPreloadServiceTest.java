package com.livetix.service;

import com.livetix.entity.Show;
import com.livetix.mapper.ShowMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockPreloadService — 库存预热测试")
class StockPreloadServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private ShowMapper showMapper;

    @InjectMocks
    private StockPreloadService stockPreloadService;

    @Test
    @DisplayName("预热成功")
    void testPreloadStock_Success() {
        Show show = new Show(); show.setId(1L); show.setAvailableStock(500);
        when(showMapper.selectById(1L)).thenReturn(show);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        int stock = stockPreloadService.preloadStock(1L);
        assertEquals(500, stock);
    }

    @Test
    @DisplayName("演出不存在时抛异常")
    void testPreloadStock_ShowNotFound() {
        when(showMapper.selectById(999L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> stockPreloadService.preloadStock(999L));
    }
}
