package com.livetix.controller.admin;

import com.livetix.common.Result;
import com.livetix.entity.Order;
import com.livetix.entity.WalletTransaction;
import com.livetix.entity.User;
import com.livetix.mapper.ShowMapper;
import com.livetix.mapper.UserMapper;
import com.livetix.mapper.WalletTransactionMapper;
import com.livetix.service.NotificationService;
import com.livetix.service.OrderService;
import com.livetix.service.StockPreloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin: Order management
 *
 * 16 修复: refund 加 @Transactional + 退钱到钱包 + use restoreStockSafe + 记流水 + 发通知
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    private final ShowMapper showMapper;

    private final UserMapper userMapper;

    private final WalletTransactionMapper walletTransactionMapper;

    private final NotificationService notificationService;

    private final StockPreloadService stockPreloadService;

    @GetMapping
    public Result<?> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return orderService.listAllOrders(page, pageSize, status, keyword);
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        var order = orderService.getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        return Result.ok(order);
    }

    /**
     * 16 修复: 管理员退款 — @Transactional + 原子退钱 + 安全恢复库存 + 流水 + 通知
     */
    @PutMapping("/{id}/refund")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> refund(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!"paid".equals(order.getStatus())) {
            return Result.fail("仅已支付订单可退款");
        }

        // ① 更新订单状态
        order.setStatus("refunded");
        order.setRefundTime(LocalDateTime.now());
        orderService.updateById(order);

        // ② 安全恢复库存（用 restoreStockSafe，避免超量恢复）+ 回补 Redis 预热池
        int restored = showMapper.restoreStockSafe(order.getShowId(), order.getQuantity());
        if (restored > 0) {
            stockPreloadService.restorePreloadStock(order.getShowId(), order.getQuantity());
        }

        // ③ 退款到用户余额（原子 ADD）
        BigDecimal refundAmount = order.getPayAmount();
        // 全额退款（也可根据退票政策计算手续费，此处默认全额退）
        int rows = userMapper.addBalance(order.getUserId(), refundAmount);
        if (rows == 0) {
            throw new RuntimeException("退款失败：用户不存在");
        }

        // ④ 查询退款后余额
        User user = userMapper.selectById(order.getUserId());
        BigDecimal balanceAfter = user != null ? user.getBalance() : BigDecimal.ZERO;

        // ⑤ 记录财务流水
        WalletTransaction wt = new WalletTransaction();
        wt.setUserId(order.getUserId());
        wt.setType("refund");
        wt.setAmount(refundAmount);
        wt.setBalanceAfter(balanceAfter);
        wt.setOrderId(order.getId());
        wt.setRemark("管理员退款 - " + order.getShowTitle() + " " + order.getTicketType()
                + " ×" + order.getQuantity() + "张");
        walletTransactionMapper.insert(wt);

        // ⑥ 发送通知
        try {
            notificationService.send(order.getUserId(), "refund_result",
                    "退款到账", "您的订单 " + order.getOrderNo() + " 已退款 ¥" + refundAmount + " 到钱包余额。");
        } catch (Exception e) {
            // 通知失败不影响退款
        }

        return Result.ok("退款成功，已退还 ¥" + refundAmount);
    }
}
