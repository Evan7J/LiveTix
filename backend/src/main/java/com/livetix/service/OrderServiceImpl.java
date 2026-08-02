package com.livetix.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livetix.common.Result;
import com.livetix.common.constant.RedisKey;
import com.livetix.common.exception.BusinessException;
import com.livetix.dto.OrderCreateDTO;
import com.livetix.entity.Order;
import com.livetix.entity.Show;
import com.livetix.entity.WalletTransaction;
import com.livetix.mapper.OrderMapper;
import com.livetix.mapper.ShowMapper;
import com.livetix.mapper.UserMapper;
import com.livetix.mapper.VenueMapper;
import com.livetix.mapper.WalletTransactionMapper;
import com.livetix.mq.OrderMessageProducer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务实现 — 核心下单逻辑
 * 防超卖方案：Redis Lua 预扣 → RocketMQ 异步落库 → DB 乐观锁兜底
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final ShowMapper showMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    private final NotificationService notificationService;

    private final StockPreloadService stockPreloadService;

    private final VenueMapper venueMapper;

    private final WalletTransactionMapper walletTransactionMapper;

    private final UserMapper userMapper;

    /**
     * RocketMQ 可选依赖：未配置 name-server 时此 bean 不存在，orderMessageProducer 为 null
     * 此时下单降级为同步落库路径
     */
    @Autowired(required = false)
    private OrderMessageProducer orderMessageProducer;

    @Value("${livetix.order.cancel-batch-size:100}")
    private int cancelBatchSize;

    private static final long ORDER_TIMEOUT_MINUTES = 15;
    private static final String RUSH_TOKEN_BUCKET_KEY = "livetix:rush:token_bucket";
    @Value("${livetix.order.rush-token-bucket-capacity:500}")
    private int rushTokenCapacity;
    @Value("${livetix.order.rush-token-fill-rate:200}")
    private int rushTokenFillRate;

    private static final String USER_ORDER_LOCK_KEY = "livetix:user:order:lock:";
    private static final long USER_ORDER_LOCK_TTL = 10;
    private static final String PAY_LOCK_KEY = "livetix:pay:lock:";
    private static final String STOCK_PRE_KEY = "livetix:stock:pre:";

    private DefaultRedisScript<Long> tokenBucketScript;
    private DefaultRedisScript<Long> stockDeductScript;

    @PostConstruct
    public void init() {
        tokenBucketScript = new DefaultRedisScript<>();
        tokenBucketScript.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        tokenBucketScript.setResultType(Long.class);

        stockDeductScript = new DefaultRedisScript<>();
        stockDeductScript.setLocation(new ClassPathResource("scripts/stock_deduct.lua"));
        stockDeductScript.setResultType(Long.class);
    }

    @Override
    public Result<?> createOrder(Long userId, OrderCreateDTO dto) {
        Long showId = dto.getShowId();
        String ticketType = dto.getTicketType();
        int quantity = dto.getQuantity();

        // 1. 请求级幂等
        if (dto.getRequestId() != null && !dto.getRequestId().isBlank()) {
            String idempotentKey = "livetix:idempotent:" + dto.getRequestId();
            Boolean idempotent = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", 5, TimeUnit.MINUTES);
            if (Boolean.FALSE.equals(idempotent)) {
                return Result.fail("请勿重复提交");
            }
        }

        // 2. 令牌桶限流
        if (!acquireRushTokenAtomic()) {
            return Result.fail("当前抢票人数过多，请稍后再试");
        }

        // 3. 用户防重
        String userLockKey = USER_ORDER_LOCK_KEY + userId + ":" + showId;
        Boolean userLocked = redisTemplate.opsForValue()
                .setIfAbsent(userLockKey, "1", USER_ORDER_LOCK_TTL, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(userLocked)) {
            return Result.fail("请勿重复提交订单");
        }

        // 4. 校验演出
        Show show = getShowFromCache(showId);
        if (show == null) {
            redisTemplate.delete(userLockKey);
            return Result.fail("演出不存在");
        }
        if (show.getStatus() != 1) {
            redisTemplate.delete(userLockKey);
            return Result.fail("该演出暂未开售或已结束");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(show.getSaleStartTime()) || now.isAfter(show.getSaleEndTime())) {
            redisTemplate.delete(userLockKey);
            return Result.fail("不在销售时间范围内");
        }
        if (show.getBuyLimit() != null && show.getBuyLimit() > 0) {
            if (quantity > show.getBuyLimit()) {
                redisTemplate.delete(userLockKey);
                return Result.fail("每人限购" + show.getBuyLimit() + "张，当前购买" + quantity + "张");
            }
            int userBought = baseMapper.countUserTicketsForShow(userId, showId);
            if (userBought + quantity > show.getBuyLimit()) {
                redisTemplate.delete(userLockKey);
                int remain = show.getBuyLimit() - userBought;
                return Result.fail(remain > 0
                    ? "每人限购" + show.getBuyLimit() + "张，您已购买" + userBought + "张，还可购买" + remain + "张"
                    : "每人限购" + show.getBuyLimit() + "张，您已达到购买上限");
            }
        }

        if (show.getIsRealName() != null && show.getIsRealName() == 1) {
            if (dto.getRealNameId() == null) {
                redisTemplate.delete(userLockKey);
                return Result.fail("该演出为实名制购票，请先选择观演人");
            }
        }

        String pendingKey = "livetix:user:pending:" + userId + ":" + showId;
        Object pendingObj = redisTemplate.opsForValue().get(pendingKey);
        long pendingCount = pendingObj != null ? Long.parseLong(pendingObj.toString()) : 0;
        if (pendingCount >= 3) {
            redisTemplate.delete(userLockKey);
            return Result.fail("您有未支付订单，请先完成或取消后再下单");
        }

        // 5. Lua 原子预扣 Redis 库存
        String stockKey = STOCK_PRE_KEY + showId;
        Long remaining = redisTemplate.execute(
                stockDeductScript,
                Collections.singletonList(stockKey),
                quantity, show.getAvailableStock());

        if (remaining == null || remaining < 0) {
            redisTemplate.delete(userLockKey);
            return Result.fail("库存不足");
        }

        evictShowCache(showId);

        // 6. 发送 RocketMQ 消息异步落库
        String preLockKey = stockKey + ":" + userId + ":" + System.currentTimeMillis();
        if (orderMessageProducer != null) {
            if (dto.getRequestId() == null || dto.getRequestId().isBlank()) {
                dto.setRequestId(IdUtil.fastSimpleUUID());
            }
            orderMessageProducer.sendOrderCreateMessage(userId, dto, preLockKey);
            log.info("Order queued to MQ: userId={}, showId={}, qty={}, remaining={}",
                    userId, showId, quantity, remaining);
            return Result.ok("下单请求已提交，正在排队处理", Map.of(
                    "pending", true,
                    "requestId", dto.getRequestId()));
        }

        // 降级路径：MQ 不可用 → 同步落库
        try {
            Result<?> result = createOrderAsync(userId, dto, preLockKey);
            if (result.getData() instanceof Order order) {
                log.info("Order created sync (MQ unavailable): orderNo={}, userId={}, showId={}, qty={}, remaining={}",
                        order.getOrderNo(), userId, showId, quantity, remaining);
                return Result.ok("下单成功", order);
            }
            return result;
        } catch (Exception e) {
            log.error("Order create sync failed, rolling back Redis stock", e);
            rollbackRedisStock(showId, quantity);
            return Result.fail("下单失败，请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> createOrderAsync(Long userId, OrderCreateDTO dto, String preLockKey) {
        Long showId = dto.getShowId();
        String ticketType = dto.getTicketType();
        int quantity = dto.getQuantity();

        Show show = showMapper.selectById(showId);
        if (show == null || show.getStatus() != 1) {
            rollbackRedisStock(showId, quantity);
            return Result.fail("演出状态异常");
        }

        if (show.getBuyLimit() != null && show.getBuyLimit() > 0) {
            int userBought = baseMapper.countUserTicketsForShow(userId, showId);
            if (userBought + quantity > show.getBuyLimit()) {
                rollbackRedisStock(showId, quantity);
                log.warn("Async: buy limit exceeded for userId={}, showId={}, alreadyBought={}, limit={}",
                        userId, showId, userBought, show.getBuyLimit());
                return Result.fail("每人限购" + show.getBuyLimit() + "张，您已达到购买上限");
            }
        }

        if (show.getIsRealName() != null && show.getIsRealName() == 1) {
            if (dto.getRealNameId() == null) {
                rollbackRedisStock(showId, quantity);
                return Result.fail("该演出为实名制购票，请先选择观演人");
            }
        }

        int rows = showMapper.deductStock(showId, quantity);
        if (rows == 0) {
            rollbackRedisStock(showId, quantity);
            return Result.fail("库存不足");
        }

        BigDecimal totalAmount = BigDecimal.valueOf(dto.getTicketPrice())
                .multiply(BigDecimal.valueOf(quantity));
        BigDecimal payAmount = totalAmount;

        LocalDateTime now = LocalDateTime.now();

        Show updatedShow = showMapper.selectById(showId);
        if (updatedShow != null && updatedShow.getAvailableStock() != null
                && updatedShow.getAvailableStock() <= 0) {
            updatedShow.setStatus(2);
            showMapper.updateById(updatedShow);
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setShowId(showId);
        order.setShowTitle(show.getTitle());
        order.setShowTime(show.getShowTime());
        order.setCoverImage(show.getCoverImage());
        if (show.getVenueId() != null) {
            var venue = venueMapper.selectById(show.getVenueId());
            if (venue != null) order.setVenueName(venue.getName());
        }
        order.setTicketType(ticketType);
        order.setTicketPrice(BigDecimal.valueOf(dto.getTicketPrice()));
        order.setQuantity(quantity);

        String seatLabel = dto.getSeats() != null && !dto.getSeats().isBlank() ? dto.getSeats() : null;
        String seatCoord = dto.getSeatCells() != null && !dto.getSeatCells().isBlank() ? dto.getSeatCells() : null;
        if (seatLabel != null && seatCoord != null) {
            order.setSeats(seatLabel + "|" + seatCoord);
        } else if (seatLabel != null) {
            order.setSeats(seatLabel);
        } else {
            order.setSeats(seatCoord);
        }
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayAmount(payAmount);
        order.setStatus("pending");
        order.setRemark(dto.getRemark());
        if (dto.getRealNameId() != null) {
            order.setRealNameIds(String.valueOf(dto.getRealNameId()));
        }

        int timeoutMinutes = show.getPayTimeoutMinutes() != null && show.getPayTimeoutMinutes() > 0
                ? show.getPayTimeoutMinutes() : (int) ORDER_TIMEOUT_MINUTES;
        order.setPayExpireTime(now.plusMinutes(timeoutMinutes));

        this.save(order);

        if (orderMessageProducer != null) {
            try {
                orderMessageProducer.sendDelayedCancelMessage(order.getId(), order.getOrderNo(), timeoutMinutes);
            } catch (Exception e) {
                log.warn("Failed to send delayed cancel message, orderNo={}", order.getOrderNo(), e);
            }
        }

        try {
            notificationService.send(userId, "order",
                "下单成功", "您已成功下单「" + show.getTitle() + "」" + ticketType
                + " ×" + quantity + "张，请在" + timeoutMinutes + "分钟内完成支付。", order.getId());
        } catch (Exception e) {
            log.warn("Failed to send order notification: {}", e.getMessage());
        }

        log.info("Order created (async MQ): {} | show={} qty={} total={}",
                order.getOrderNo(), showId, quantity, totalAmount);

        return Result.ok("下单成功", order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> cancelIfStillPending(Long orderId) {
        Order order = this.getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!"pending".equals(order.getStatus())) {
            return Result.ok("订单已处理，无需取消");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = order.getPayExpireTime() != null
                ? order.getPayExpireTime()
                : order.getCreateTime().plusMinutes(ORDER_TIMEOUT_MINUTES);
        if (now.isBefore(deadline)) {
            return Result.ok("订单尚未超时");
        }

        int rows = showMapper.restoreStockSafe(order.getShowId(), order.getQuantity());
        if (rows > 0) {
            stockPreloadService.restorePreloadStock(order.getShowId(), order.getQuantity());
            order.setStatus("cancelled");
            order.setCancelTime(now);
            this.updateById(order);
            restoreShowOnSaleStatus(order.getShowId());
        }

        log.info("Order cancelled by delayed MQ: {} show={} qty={}",
                order.getOrderNo(), order.getShowId(), order.getQuantity());

        return Result.ok("已取消");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> processPayment(String orderNo, String payMethod) {
        Order existing = this.getOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        if (existing == null) {
            return Result.fail("订单不存在");
        }
        if ("paid".equals(existing.getStatus())) {
            return Result.ok("订单已支付", Map.of("orderNo", orderNo));
        }
        if (!"pending".equals(existing.getStatus())) {
            return Result.fail("订单状态不允许支付");
        }

        String payLockKey = PAY_LOCK_KEY + orderNo;
        Boolean payLocked = redisTemplate.opsForValue()
                .setIfAbsent(payLockKey, "1", 30, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(payLocked)) {
            return Result.fail("支付正在处理中，请勿重复操作");
        }

        try {
            Order order = this.getOne(new LambdaQueryWrapper<Order>()
                    .eq(Order::getOrderNo, orderNo));

            if (order == null) {
                return Result.fail("订单不存在");
            }
            if (!"pending".equals(order.getStatus())) {
                return Result.fail("订单状态不允许支付");
            }

            BigDecimal balanceAfter = BigDecimal.ZERO;

            if ("wallet".equals(payMethod)) {
                int deducted = userMapper.deductBalance(order.getUserId(), order.getPayAmount());
                if (deducted == 0) {
                    return Result.badRequest("余额不足，请先充值后再支付");
                }
                com.livetix.entity.User user = userMapper.selectById(order.getUserId());
                balanceAfter = user != null && user.getBalance() != null
                        ? user.getBalance() : BigDecimal.ZERO;
            }

            boolean transitioned = this.lambdaUpdate()
                    .eq(Order::getOrderNo, orderNo)
                    .eq(Order::getStatus, "pending")
                    .set(Order::getStatus, "paid")
                    .set(Order::getPayTime, LocalDateTime.now())
                    .set(Order::getPayMethod, payMethod)
                    .update();
            if (!transitioned) {
                throw new BusinessException("订单已被处理，请勿重复支付");
            }

            WalletTransaction wt = new WalletTransaction();
            wt.setUserId(order.getUserId());
            wt.setType("purchase");
            wt.setAmount(order.getPayAmount().negate());
            wt.setBalanceAfter("wallet".equals(payMethod) ? balanceAfter : BigDecimal.ZERO);
            wt.setOrderId(order.getId());
            wt.setRemark("购票 - " + order.getShowTitle() + " " + order.getTicketType()
                    + " ×" + order.getQuantity() + "张"
                    + ("wallet".equals(payMethod) ? " [余额支付]" : ""));
            walletTransactionMapper.insert(wt);

            try {
                notificationService.send(order.getUserId(), "payment",
                    "支付成功", "您的订单 " + order.getOrderNo() + " 已支付成功，请在演出当天凭电子票入场。",
                    order.getId());
            } catch (Exception e) {
                log.warn("Failed to send payment notification: {}", e.getMessage());
            }

            return Result.ok("支付成功", Map.of("orderNo", order.getOrderNo()));
        } finally {
            redisTemplate.delete(payLockKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTimeoutOrders() {
        LocalDateTime now = LocalDateTime.now();
        int pageNum = 1;

        while (true) {
            Page<Order> page = this.page(new Page<>(pageNum, cancelBatchSize),
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getStatus, "pending")
                            .orderByAsc(Order::getCreateTime));

            List<Order> pendingOrders = page.getRecords();
            if (pendingOrders.isEmpty()) break;

            int cancelled = 0;
            for (Order order : pendingOrders) {
                LocalDateTime deadline = order.getPayExpireTime() != null
                        ? order.getPayExpireTime()
                        : order.getCreateTime().plusMinutes(ORDER_TIMEOUT_MINUTES);
                if (now.isBefore(deadline)) continue;

                int rows = showMapper.restoreStockSafe(order.getShowId(), order.getQuantity());
                if (rows > 0) {
                    stockPreloadService.restorePreloadStock(order.getShowId(), order.getQuantity());
                    order.setStatus("cancelled");
                    order.setCancelTime(LocalDateTime.now());
                    this.updateById(order);
                    restoreShowOnSaleStatus(order.getShowId());
                    cancelled++;
                }
            }

            if (cancelled > 0) log.info("Fallback batch {}: cancelled {} timeout orders", pageNum, cancelled);
            if (pendingOrders.size() < cancelBatchSize || cancelled == 0) break;
            pageNum++;
        }
    }

    @Override
    public Result<?> getMyOrders(Long userId, Integer page, Integer pageSize, String status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime);
        if (status != null && !status.isBlank()) wrapper.eq(Order::getStatus, status);
        Page<Order> result = this.page(new Page<>(page, pageSize), wrapper);

        if (!result.getRecords().isEmpty()) {
            java.util.Set<Long> showIds = result.getRecords().stream()
                    .map(Order::getShowId).collect(java.util.stream.Collectors.toSet());
            java.util.Map<Long, Show> showMap = new java.util.HashMap<>();
            if (!showIds.isEmpty()) {
                showMapper.selectBatchIds(showIds).forEach(s -> showMap.put(s.getId(), s));
            }
            for (Order order : result.getRecords()) {
                Show show = showMap.get(order.getShowId());
                if (show != null) {
                    order.setAllowRefund(show.getAllowRefund());
                    order.setRefundDeadlineHours(show.getRefundDeadlineHours());
                    order.setRefundFeePercent(show.getRefundFeePercent());
                }
            }
        }

        return Result.ok(result);
    }

    @Override
    public Result<?> listAllOrders(Integer page, Integer pageSize, String status, String keyword) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) wrapper.eq(Order::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Order::getOrderNo, keyword).or().like(Order::getShowTitle, keyword));
        }
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> result = this.page(new Page<>(page, pageSize), wrapper);
        return Result.ok(result);
    }

    @Override
    public Result<?> getBuyQuota(Long userId, Long showId) {
        Show show = showMapper.selectById(showId);
        if (show == null) {
            return Result.fail("演出不存在");
        }
        int buyLimit = show.getBuyLimit() != null ? show.getBuyLimit() : 0;
        int userBought = baseMapper.countUserTicketsForShow(userId, showId);
        int remaining = buyLimit > 0 ? Math.max(0, buyLimit - userBought) : Integer.MAX_VALUE;
        return Result.ok(Map.of(
                "buyLimit", buyLimit,
                "alreadyBought", userBought,
                "remaining", remaining,
                "canBuy", remaining > 0
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> cancelOrder(Long userId, Long orderId) {
        Order order = this.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) return Result.fail("订单不存在");
        if (!"pending".equals(order.getStatus())) return Result.fail("订单状态不允许取消");

        int restored = showMapper.restoreStockSafe(order.getShowId(), order.getQuantity());
        if (restored > 0) {
            stockPreloadService.restorePreloadStock(order.getShowId(), order.getQuantity());
        }
        order.setStatus("cancelled");
        order.setCancelTime(LocalDateTime.now());
        this.updateById(order);
        restoreShowOnSaleStatus(order.getShowId());

        return Result.ok("取消成功");
    }

    // ==================== 内部工具方法 ====================

    private boolean acquireRushTokenAtomic() {
        try {
            long now = System.currentTimeMillis() / 1000;
            String tokensKey = RUSH_TOKEN_BUCKET_KEY + ":tokens";
            String fillTimeKey = RUSH_TOKEN_BUCKET_KEY + ":last_fill";

            Long result = redisTemplate.execute(
                    tokenBucketScript,
                    List.of(tokensKey, fillTimeKey),
                    rushTokenCapacity, rushTokenFillRate, now);
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Token bucket script failed, REJECTING request", e);
            return false;
        }
    }

    private void rollbackRedisStock(Long showId, int quantity) {
        if (showId == null || quantity <= 0) return;
        try {
            String redisKey = STOCK_PRE_KEY + showId;
            redisTemplate.opsForValue().increment(redisKey, quantity);
            log.info("Rolled back Redis stock: key={}, qty={}", redisKey, quantity);
        } catch (Exception e) {
            log.error("Failed to rollback Redis stock: showId={}, qty={}", showId, quantity, e);
        }
    }

    private Show getShowFromCache(Long showId) {
        String cacheKey = RedisKey.SHOW_DETAIL + showId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("__NULL__".equals(cached)) return null;
            if (cached instanceof Show) return (Show) cached;
        }
        Show show = showMapper.selectById(showId);
        if (show != null) {
            redisTemplate.opsForValue().set(cacheKey, show, 30, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(cacheKey, "__NULL__", 60, TimeUnit.SECONDS);
        }
        return show;
    }

    /**
     * 清除演出相关缓存（库存变更后调用）
     * 直接删除缓存，下次请求时由 ShowServiceImpl 的互斥锁机制重建，防止缓存击穿
     */
    private void evictShowCache(Long showId) {
        redisTemplate.delete(RedisKey.SHOW_DETAIL + showId);
        redisTemplate.delete(RedisKey.SHOW_STOCK + showId);
        redisTemplate.delete(RedisKey.HOT_SHOWS);
    }

    private String generateOrderNo() {
        return "LT" + IdUtil.getSnowflakeNextIdStr();
    }

    private void restoreShowOnSaleStatus(Long showId) {
        Show show = showMapper.selectById(showId);
        if (show != null && show.getStatus() == 2
                && show.getAvailableStock() != null && show.getAvailableStock() > 0) {
            show.setStatus(1);
            show.setShowStatus("onsale");
            showMapper.updateById(show);
            log.info("Show {} restored to on-sale, stock: {}", showId, show.getAvailableStock());
        }
    }
}