package com.livetix.mq;

import com.livetix.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task: cancel unpaid orders every 60 seconds
 *
 * Final consistency mechanism:
 * - Orders created but not paid within 15 minutes are automatically cancelled
 * - Inventory is restored via DB atomic UPDATE
 * - This ensures stock is never lost even if payment fails silently
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
