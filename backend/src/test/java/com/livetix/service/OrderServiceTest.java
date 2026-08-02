package com.livetix.service;

import com.livetix.common.Result;
import com.livetix.dto.OrderCreateDTO;
import com.livetix.entity.Show;
import com.livetix.mapper.*;
import com.livetix.mq.OrderMessageProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService — 秒杀下单核心逻辑")
class OrderServiceTest {

    @Mock private ShowMapper showMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private NotificationService notificationService;
    @Mock private OrderMessageProducer orderMessageProducer;
    @Mock private UserMapper userMapper;
    @Mock private VenueMapper venueMapper;
    @Mock private WalletTransactionMapper walletTransactionMapper;

    @InjectMocks
    private OrderService orderService;

    private Show buildShow() {
        Show s = new Show();
        s.setId(1L); s.setTitle("测试演唱会"); s.setStatus(1);
        s.setSaleStartTime(LocalDateTime.now().minusDays(1));
        s.setSaleEndTime(LocalDateTime.now().plusDays(1));
        s.setAvailableStock(1000); s.setBuyLimit(4); s.setVenueId(1L);
        return s;
    }

    private OrderCreateDTO buildDTO() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setShowId(1L); dto.setTicketType("VIP");
        dto.setTicketPrice(880.0); dto.setQuantity(1);
        dto.setRequestId("uuid-test-001");
        return dto;
    }

    @Test
    @DisplayName("重复提交 requestId → 拒绝")
    void testDuplicateRequestId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // 幂等校验失败
        when(valueOps.setIfAbsent(contains("idempotent"), any(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        Result<?> r = orderService.createOrder(1L, buildDTO());
        assertNotEquals(200, r.getCode());
        assertTrue(r.getMessage().contains("重复"));
    }

    @Test
    @DisplayName("演出不存在 → 返回错误")
    void testShowNotFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // 幂等通过
        when(valueOps.setIfAbsent(contains("idempotent"), any(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        // 防重锁通过
        when(valueOps.setIfAbsent(contains("user:order:lock"), any(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        // 缓存命中空值
        when(valueOps.get(contains("show:"))).thenReturn("__NULL__");

        Result<?> r = orderService.createOrder(1L, buildDTO());
        assertNotEquals(200, r.getCode());
        assertTrue(r.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("不在销售时间范围 → 返回错误")
    void testNotInSaleWindow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(valueOps.get(contains("show:"))).thenReturn(null);
        when(showMapper.selectById(1L)).thenReturn(null); // DB也查不到

        Result<?> r = orderService.createOrder(1L, buildDTO());
        assertNotEquals(200, r.getCode());
    }
}
