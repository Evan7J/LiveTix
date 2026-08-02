package com.livetix.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livetix.common.Result;
import com.livetix.entity.*;
import com.livetix.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl extends ServiceImpl<RefundRequestMapper, RefundRequest> implements RefundService {

    private final OrderMapper orderMapper;
    private final ShowMapper showMapper;
    private final NotificationService notificationService;
    private final WalletTransactionMapper walletTransactionMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> applyRefund(Long userId, Long orderId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            return Result.fail("订单不存在");
        }
        if (!"paid".equals(order.getStatus())) {
            return Result.fail("订单状态不允许退票");
        }

        Show show = showMapper.selectById(order.getShowId());
        if (show == null || show.getAllowRefund() == null || show.getAllowRefund() == 0) {
            return Result.fail("该演出不支持退票");
        }

        if (show.getRefundDeadlineHours() != null && show.getShowTime() != null) {
            LocalDateTime deadline = show.getShowTime().minusHours(show.getRefundDeadlineHours());
            if (LocalDateTime.now().isAfter(deadline)) {
                return Result.fail("已超过退票时限（开演前" + show.getRefundDeadlineHours() + "小时）");
            }
        }

        BigDecimal feeRate = show.getRefundFeePercent() != null
                ? show.getRefundFeePercent().divide(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        BigDecimal fee = order.getPayAmount().multiply(feeRate).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal refundAmount = order.getPayAmount().subtract(fee);

        long existingCount = this.count(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getOrderId, orderId)
                .eq(RefundRequest::getStatus, "pending"));
        if (existingCount > 0) {
            return Result.fail("已有待审核的退票申请");
        }

        RefundRequest req = new RefundRequest();
        req.setOrderId(orderId);
        req.setUserId(userId);
        req.setReason(reason);
        req.setRefundAmount(refundAmount);
        req.setFeeAmount(fee);
        req.setStatus("pending");
        this.save(req);

        order.setStatus("refunding");
        orderMapper.updateById(order);

        log.info("Refund request created: order={}, refundAmount={}, fee={}", order.getOrderNo(), refundAmount, fee);
        return Result.ok("退票申请已提交", req);
    }

    @Override
    public Result<?> listRefunds(Integer page, Integer pageSize, String status) {
        LambdaQueryWrapper<RefundRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(RefundRequest::getStatus, status);
        }
        wrapper.orderByDesc(RefundRequest::getCreateTime);
        Page<RefundRequest> result = this.page(new Page<>(page, pageSize), wrapper);

        if (!result.getRecords().isEmpty()) {
            var orderIds = result.getRecords().stream().map(RefundRequest::getOrderId).distinct().toList();
            var userIds = result.getRecords().stream().map(RefundRequest::getUserId).distinct().toList();

            Map<Long, Order> orderMap = new HashMap<>();
            Map<Long, User> userMap = new HashMap<>();
            if (!orderIds.isEmpty()) {
                orderMapper.selectBatchIds(orderIds).forEach(o -> orderMap.put(o.getId(), o));
            }
            if (!userIds.isEmpty()) {
                userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
            }

            for (RefundRequest req : result.getRecords()) {
                Order order = orderMap.get(req.getOrderId());
                if (order != null) {
                    req.setOrderNo(order.getOrderNo());
                    req.setShowTitle(order.getShowTitle());
                    req.setTicketType(order.getTicketType());
                    req.setQuantity(order.getQuantity());
                }
                User user = userMap.get(req.getUserId());
                if (user != null) {
                    req.setUsername(user.getUsername());
                }
            }
        }

        return Result.ok(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> approveRefund(Long refundId, String comment) {
        RefundRequest req = this.getById(refundId);
        if (req == null || !"pending".equals(req.getStatus())) {
            return Result.fail("退票申请不存在或已处理");
        }

        long reviewerId = StpUtil.getLoginIdAsLong();

        req.setStatus("approved");
        req.setReviewerId(reviewerId);
        req.setReviewComment(comment);
        req.setReviewTime(LocalDateTime.now());
        this.updateById(req);

        Order order = orderMapper.selectById(req.getOrderId());
        order.setStatus("refunded");
        order.setRefundTime(LocalDateTime.now());
        orderMapper.updateById(order);

        showMapper.restoreStock(order.getShowId(), order.getQuantity());

        int rows = userMapper.addBalance(order.getUserId(), req.getRefundAmount());
        if (rows == 0) {
            return Result.fail("退款失败：用户不存在");
        }
        User user = userMapper.selectById(order.getUserId());
        BigDecimal balanceAfter = user != null ? user.getBalance() : BigDecimal.ZERO;

        WalletTransaction wt = new WalletTransaction();
        wt.setUserId(order.getUserId());
        wt.setType("refund");
        wt.setAmount(req.getRefundAmount());
        wt.setBalanceAfter(balanceAfter);
        wt.setOrderId(order.getId());
        wt.setRemark("退票退款 - " + order.getOrderNo());
        walletTransactionMapper.insert(wt);

        notificationService.send(order.getUserId(), "refund_result",
                "退票申请已通过", "您的订单 " + order.getOrderNo() + " 退票已通过，退款 ¥" + req.getRefundAmount() + " 已退回余额。");

        log.info("Refund approved: order={}, amount={}", order.getOrderNo(), req.getRefundAmount());
        return Result.ok("退票审核通过");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> rejectRefund(Long refundId, String comment) {
        RefundRequest req = this.getById(refundId);
        if (req == null || !"pending".equals(req.getStatus())) {
            return Result.fail("退票申请不存在或已处理");
        }

        long reviewerId = StpUtil.getLoginIdAsLong();
        req.setStatus("rejected");
        req.setReviewerId(reviewerId);
        req.setReviewComment(comment);
        req.setReviewTime(LocalDateTime.now());
        this.updateById(req);

        Order order = orderMapper.selectById(req.getOrderId());
        order.setStatus("paid");
        orderMapper.updateById(order);

        notificationService.send(order.getUserId(), "refund_result",
                "退票申请被拒绝", "您的订单 " + order.getOrderNo() + " 退票申请被拒绝。原因：" + (comment != null ? comment : "不符合退票条件"));

        log.info("Refund rejected: order={}", order.getOrderNo());
        return Result.ok("已拒绝退票");
    }
}