package com.livetix.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetix.common.constant.RedisKey;
import com.livetix.dto.OrderCreateDTO;
import com.livetix.entity.Order;
import com.livetix.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * P1-2: RocketMQ 异步下单消费者
 * P1-3: 延迟订单取消消费者
 *
 * M1 修复: 消费失败时回滚 Redis 预扣库存 + 释放用户防重锁
 * M2 修复: 同 OrderMessageProducer — @ConditionalOnBean 评估时机早于 auto-configuration，
 *          改用 @ConditionalOnProperty
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.name-server")
public class OrderMessageConsumer {

    private final OrderService orderService;

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STOCK_PRE_KEY = "livetix:stock:pre:";
    private static final String USER_ORDER_LOCK_KEY = "livetix:user:order:lock:";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Component
    @RocketMQMessageListener(
            topic = "livetix-order-topic",
            selectorExpression = "order-create",
            consumerGroup = "livetix-consumer-group"
    )
    public class OrderCreateListener implements RocketMQListener<String> {
        @Override
        public void onMessage(String message) {
            Long userId = null;
            Long showId = null;
            int quantity = 0;
            String preLockKey = null;
            String requestId = null;

            try {
                Map<String, Object> payload = objectMapper.readValue(message,
                        new TypeReference<Map<String, Object>>() {});

                userId = Long.valueOf(payload.get("userId").toString());
                preLockKey = (String) payload.get("preLockKey");

                @SuppressWarnings("unchecked")
                Map<String, Object> dtoMap = (Map<String, Object>) payload.get("dto");
                OrderCreateDTO dto = objectMapper.convertValue(dtoMap, OrderCreateDTO.class);
                showId = dto.getShowId();
                quantity = dto.getQuantity();
                requestId = dto.getRequestId();

                log.info("Processing order from MQ: userId={}, showId={}", userId, showId);

                var result = orderService.createOrderAsync(userId, dto, preLockKey);
                if (result.getCode() == 200 && result.getData() instanceof Order order) {
                    // 写入下单结果，前端凭 requestId 轮询 /orders/create-status
                    writeOrderResult(userId, requestId, "OK:" + order.getId());
                } else {
                    log.warn("Order creation failed: userId={}, reason={}", userId, result.getMessage());
                    // createOrderAsync 内部已回滚 Redis 预扣库存，此处只释放防重锁 + 写失败结果
                    releaseUserLock(userId, showId);
                    writeOrderResult(userId, requestId, "FAIL:" + result.getMessage());
                }
            } catch (Exception e) {
                log.error("Failed to consume order create message, rolling back stock", e);
                // 消费异常（DB 事务已回滚，但 Redis 预扣未回滚）→ 回滚库存 + 释放锁
                if (showId != null) {
                    rollbackRedisStock(showId, quantity, userId);
                }
                writeOrderResult(userId, requestId, "FAIL:下单失败，请重试");
            }
        }
    }

    @Component
    @RocketMQMessageListener(
            topic = "livetix-delay-topic",
            selectorExpression = "order-cancel-delay",
            consumerGroup = "livetix-delay-consumer-group"
    )
    public class OrderCancelDelayListener implements RocketMQListener<String> {
        @Override
        public void onMessage(String message) {
            try {
                Map<String, Object> payload = objectMapper.readValue(message,
                        new TypeReference<Map<String, Object>>() {});

                Long orderId = Long.valueOf(payload.get("orderId").toString());
                String orderNo = (String) payload.get("orderNo");
                log.info("Processing delayed cancel: orderId={}, orderNo={}", orderId, orderNo);

                orderService.cancelIfStillPending(orderId);
            } catch (Exception e) {
                log.error("Failed to consume delayed cancel message", e);
            }
        }
    }

    /**
     * M1: 回滚 Redis 预扣库存 + 释放用户防重锁
     */
    private void rollbackRedisStock(Long showId, int quantity, Long userId) {
        try {
            if (showId != null && quantity > 0) {
                String stockKey = STOCK_PRE_KEY + showId;
                redisTemplate.opsForValue().increment(stockKey, quantity);
                log.info("Rolled back Redis stock: showId={}, qty={}", showId, quantity);
            }
            releaseUserLock(userId, showId);
        } catch (Exception e) {
            log.error("Failed to rollback Redis stock for showId={}, userId={}", showId, userId, e);
        }
    }

    /** 释放用户下单防重锁（失败后允许用户立即重试） */
    private void releaseUserLock(Long userId, Long showId) {
        try {
            if (userId != null && showId != null) {
                redisTemplate.delete(USER_ORDER_LOCK_KEY + userId + ":" + showId);
            }
        } catch (Exception e) {
            log.error("Failed to release user order lock: userId={}, showId={}", userId, showId, e);
        }
    }

    /** 写入 MQ 异步下单结果（前端轮询用），TTL 5 分钟 */
    private void writeOrderResult(Long userId, String requestId, String result) {
        if (userId == null || requestId == null || requestId.isBlank()) return;
        try {
            redisTemplate.opsForValue().set(RedisKey.ORDER_RESULT + userId + ":" + requestId,
                    result, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Failed to write order result: userId={}, requestId={}", userId, requestId, e);
        }
    }
}
