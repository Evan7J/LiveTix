package com.livetix.controller.user;

import cn.dev33.satoken.stp.StpUtil;
import com.livetix.common.Result;
import com.livetix.entity.Order;
import com.livetix.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付控制器，当前为模拟支付，预留真实对接接口
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class PaymentController {

    private final OrderService orderService;

    /**
     * 生成支付订单，返回支付参数供前端展示
     */
    @PostMapping("/pay/{orderId}/prepare")
    public Result<?> preparePay(@PathVariable Long orderId) {
        long userId = StpUtil.getLoginIdAsLong();
        Order order = orderService.getById(orderId);

        if (order == null || !order.getUserId().equals(userId)) {
            return Result.fail("订单不存在");
        }
        if (!"pending".equals(order.getStatus())) {
            return Result.fail("订单状态不允许支付");
        }

        // 生成支付参数（模拟）
        // 真实对接时调用微信/支付宝 SDK 统一下单接口
        String payUrl = "/api/user/pay/" + orderId + "/execute";
        String qrCode = "https://api.livetix.com/qr/" + order.getOrderNo();

        Map<String, Object> payParams = Map.of(
                "orderNo", order.getOrderNo(),
                "payAmount", order.getPayAmount(),
                "payUrl", payUrl,
                "qrCode", qrCode,
                "expireTime", order.getPayExpireTime() != null
                        ? order.getPayExpireTime().toString() : null
        );

        return Result.ok("支付订单已生成", payParams);
    }

    /**
     * 执行支付，模拟支付流程
     */
    @PostMapping("/pay/{orderId}/execute")
    public Result<?> executePay(@PathVariable Long orderId, @RequestBody Map<String, String> body) {
        long userId = StpUtil.getLoginIdAsLong();
        Order order = orderService.getById(orderId);

        if (order == null || !order.getUserId().equals(userId)) {
            return Result.fail("订单不存在");
        }
        if (!"pending".equals(order.getStatus())) {
            return Result.fail("订单状态不允许支付");
        }

        String payMethod = body != null ? body.getOrDefault("method", "wechat") : "wechat";

        // 执行支付（钱包余额支付会校验余额）
        return orderService.processPayment(order.getOrderNo(), payMethod);
    }

    // 支付回调已移至 PublicController
}
