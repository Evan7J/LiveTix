package com.livetix.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetix.common.constant.RedisKey;
import com.livetix.dto.OrderCreateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * RocketMQ 异步下单生产者
 * 仅在配置了 rocketmq.name-server 时生效
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.name-server")
public class OrderMessageProducer {

    private final RocketMQTemplate rocketMQTemplate;

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ORDER_TOPIC = "livetix-order-topic";
    private static final String ORDER_CREATE_TAG = "order-create";
    private static final String STOCK_PRE_KEY = "livetix:stock:pre:";
    private static final String USER_ORDER_LOCK_KEY = "livetix:user:order:lock:";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendOrderCreateMessage(Long userId, OrderCreateDTO dto, String preLockKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("dto", dto);
        payload.put("preLockKey", preLockKey);
        payload.put("timestamp", System.currentTimeMillis());

        try {
            String json = objectMapper.writeValueAsString(payload);
            Message<String> message = MessageBuilder
                    .withPayload(json)
                    .setHeader("userId", userId)
                    .build();

            rocketMQTemplate.asyncSend(
                    ORDER_TOPIC + ":" + ORDER_CREATE_TAG,
                    message,
                    new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            log.info("Order message sent: msgId={}, userId={}", sendResult.getMsgId(), userId);
                        }

                        @Override
                        public void onException(Throwable e) {
                            log.error("MQ send FAILED — rolling back Redis stock: userId={}, showId={}",
                                    userId, dto.getShowId(), e);
                            rollbackAndNotifyFail(userId, dto);
                        }
                    },
                    3000
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize order message for userId={}, rolling back stock", userId, e);
            rollbackAndNotifyFail(userId, dto);
        }
    }

    /**
     * 发送失败回滚 Redis 预扣库存 + 释放用户防重锁
     */
    private void rollbackAndNotifyFail(Long userId, OrderCreateDTO dto) {
        String stockKey = STOCK_PRE_KEY + dto.getShowId();
        redisTemplate.opsForValue().increment(stockKey, dto.getQuantity());

        String userLockKey = USER_ORDER_LOCK_KEY + userId + ":" + dto.getShowId();
        redisTemplate.delete(userLockKey);

        if (dto.getRequestId() != null && !dto.getRequestId().isBlank()) {
            redisTemplate.opsForValue().set(RedisKey.ORDER_RESULT + userId + ":" + dto.getRequestId(),
                    "FAIL:系统繁忙，请稍后重试", 5, TimeUnit.MINUTES);
        }
    }

    public void sendDelayedCancelMessage(Long orderId, String orderNo, int timeoutMinutes) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", orderNo);
        payload.put("timeoutMinutes", timeoutMinutes);
        payload.put("createTime", System.currentTimeMillis());

        try {
            String json = objectMapper.writeValueAsString(payload);
            Message<String> message = MessageBuilder.withPayload(json).build();

            int delayLevel = resolveDelayLevel(timeoutMinutes);

            rocketMQTemplate.asyncSend(
                    "livetix-delay-topic:order-cancel-delay",
                    message,
                    new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            log.info("Delayed cancel sent: orderNo={}, delay={}min", orderNo, timeoutMinutes);
                        }
                        @Override
                        public void onException(Throwable e) {
                            log.error("Failed to send delay cancel: orderNo={}", orderNo, e);
                        }
                    },
                    3000,
                    delayLevel
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize delay cancel: orderNo={}", orderNo, e);
        }
    }

    private int resolveDelayLevel(int minutes) {
        if (minutes <= 1) return 5;
        if (minutes <= 5) return 9;
        if (minutes <= 10) return 13;
        if (minutes <= 15) return 14;
        if (minutes <= 20) return 15;
        if (minutes <= 30) return 16;
        return 17;
    }
}
