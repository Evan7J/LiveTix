package com.livetix.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livetix.common.Result;
import com.livetix.dto.OrderCreateDTO;
import com.livetix.entity.Order;

public interface OrderService extends IService<Order> {

    Result<?> createOrder(Long userId, OrderCreateDTO dto);

    Result<?> createOrderAsync(Long userId, OrderCreateDTO dto, String preLockKey);

    Result<?> cancelIfStillPending(Long orderId);

    Result<?> processPayment(String orderNo, String payMethod);

    void cancelTimeoutOrders();

    Result<?> getMyOrders(Long userId, Integer page, Integer pageSize, String status);

    Result<?> listAllOrders(Integer page, Integer pageSize, String status, String keyword);

    Result<?> getBuyQuota(Long userId, Long showId);

    Result<?> cancelOrder(Long userId, Long orderId);
}