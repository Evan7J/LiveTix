package com.livetix.mq;

import com.livetix.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：每60秒检查并取消超时未支付的订单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderService orderService;

    @Scheduled(fixedDelay = 60000)  // every 60 seconds
    public void cancelTimeoutOrders() {
        log.debug("Running order timeout check...");
        try {
            orderService.cancelTimeoutOrders();
        } catch (Exception e) {
            log.error("Order timeout check failed", e);
        }
    }
}
