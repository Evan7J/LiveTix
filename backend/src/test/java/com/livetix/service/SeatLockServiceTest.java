package com.livetix.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeatLockService — 座位锁定")
class SeatLockServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private SeatLockService seatLockService;

    @Test
    @DisplayName("超过6个座位应拒绝")
    void testTooManySeats() {
        Map<String, Object> r = seatLockService.lockSeats(
                1L, List.of("A1","A2","A3","A4","A5","A6","A7"), 100L);
        assertFalse((Boolean) r.get("success"));
        assertTrue(r.get("message").toString().contains("最多"));
    }

    @Test
    @DisplayName("空座位列表应拒绝")
    void testEmptySeats() {
        Map<String, Object> r = seatLockService.lockSeats(1L, List.of(), 100L);
        assertFalse((Boolean) r.get("success"));
    }

    @Test
    @DisplayName("SET NX 锁定成功场景")
    void testLockSuccess() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(hashOps.get(anyString(), anyString())).thenReturn("available");

        Map<String, Object> r = seatLockService.lockSeats(
                1L, List.of("A-001", "A-002"), 100L);
        assertTrue((Boolean) r.get("success"));
    }
}