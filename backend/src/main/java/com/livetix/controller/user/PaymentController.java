package com.livetix.controller.user;

import cn.dev33.satoken.stp.StpUtil;
import com.livetix.common.Result;
import com.livetix.entity.Order;
import com.livetix.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付控制器
 *
 * ============================================================
 * 支付流程说明：
 * ============================================================
 * 1. 用户下单后，前端调用 /pay/{orderId}/prepare 获取支付参数
 * 2. 后端生成支付订单（微信/支付宝统一下单），返回支付链接/二维码参数
 * 3. 前端展示支付二维码，用户扫码支付
 * 4. 支付平台异步回调 /pay/callback 通知支付结果
 * 5. 后端验证签名后更新订单状态
 *
 * 当前阶段为模拟支付，预留真实对接接口。
 * 对接真实支付时只需：
 *   - 替换 createPayOrder 中的统一下单逻辑
 *   - 实现 verifyCallbackSign 中的签名验证
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class PaymentController {

    private final OrderService orderService;

    /**
     * 生成支付订单
     *
     * POST /api/user/pay/{orderId}/prepare
     * 返回支付参数（前端据此展示支付二维码或跳转支付页面）
     *
     * 生产环境对接示例：
     *   - 微信支付：调用 JSAPI 统一下单接口，返回 prepay_id + 签名参数
     *   - 支付宝：调用 alipay.trade.precreate，返回二维码链接
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
                "qrCode", qrCode,  // 生产环境：微信 code_url 或支付宝 qr_code
                "expireTime", order.getPayExpireTime() != null
                        ? order.getPayExpireTime().toString() : null
        );

        return Result.ok("支付订单已生成", payParams);
    }

    /**
     * 执行支付（模拟）
     *
     * POST /api/user/pay/{orderId}/execute
     *
     * 当前直接标记支付成功。
     * 生产环境此接口不需要存在——支付结果通过 /pay/callback 异步通知。
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

    // 支付回调已移至 PublicController（实际路径：/api/public/pay/callback）
    // 此 Controller 的路由基路径为 /api/user，其下的 /public/pay/callback
    // 实际路径为 /api/user/public/pay/callback，不符合支付平台的回调预期。
    // 新的正确回调路径：POST /api/public/pay/callback（在 PublicController 中处理）
}
