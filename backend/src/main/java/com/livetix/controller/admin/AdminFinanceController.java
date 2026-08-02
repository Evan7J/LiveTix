package com.livetix.controller.admin;

import com.livetix.common.Result;
import com.livetix.mapper.OrderMapper;
import com.livetix.mapper.WalletTransactionMapper;
import com.livetix.entity.Order;
import com.livetix.entity.WalletTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
public class AdminFinanceController {

    private final WalletTransactionMapper walletTransactionMapper;
    private final OrderMapper orderMapper;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/transactions")
    public Result<?> transactions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String type) {
        LambdaQueryWrapper<WalletTransaction> wrapper = new LambdaQueryWrapper<>();
        if (type != null && !type.isBlank()) {
            wrapper.eq(WalletTransaction::getType, type);
        }
        wrapper.orderByDesc(WalletTransaction::getCreateTime);
        Page<WalletTransaction> result = walletTransactionMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return Result.ok(result);
    }

    @GetMapping("/refunds")
    public Result<?> refunds(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "refunded")
                .orderByDesc(Order::getRefundTime);
        Page<Order> result = orderMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return Result.ok(result);
    }

    /**
     * 19 修复: 用 SQL 聚合替代 selectList(null) 全表加载到内存
     */
    @GetMapping("/summary")
    public Result<?> summary() {
        // 单条 SQL 聚合：SUM + GROUP BY type，避免全表加载 OOM
        String sql = "SELECT type, COALESCE(SUM(CASE WHEN type='purchase' THEN -amount ELSE amount END), 0) AS amount " +
                     "FROM t_wallet_transaction GROUP BY type";
        var rows = jdbcTemplate.queryForList(sql);

        double totalRecharge = 0, totalPurchase = 0, totalRefund = 0;
        for (var row : rows) {
            String type = (String) row.get("type");
            BigDecimal amount = (BigDecimal) row.get("amount");
            double val = amount != null ? amount.doubleValue() : 0;
            switch (type) {
                case "recharge": totalRecharge = val; break;
                case "purchase": totalPurchase = val; break;
                case "refund": totalRefund = val; break;
            }
        }

        return Result.ok(Map.of(
            "totalRecharge", totalRecharge,
            "totalPurchase", totalPurchase,
            "totalRefund", totalRefund,
            "netRevenue", totalPurchase - totalRefund
        ));
    }
}
