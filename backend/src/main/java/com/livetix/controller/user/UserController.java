package com.livetix.controller.user;

import cn.dev33.satoken.stp.StpUtil;
import com.livetix.common.Result;
import com.livetix.common.exception.BusinessException;
import com.livetix.dto.OrderCreateDTO;
import com.livetix.entity.User;
import com.livetix.entity.RealNameInfo;
import com.livetix.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final OrderService orderService;
    private final RealNameService realNameService;
    private final FavoriteService favoriteService;
    private final ReminderService reminderService;
    private final NotificationService notificationService;
    private final WalletService walletService;
    private final RefundService refundService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String VERIFY_CODE_KEY = "livetix:verify:code:";

    // ==================== Profile ====================

    @GetMapping("/profile")
    public Result<?> profile() {
        return Result.ok(userService.currentUser());
    }

    /**
     * 44 修复: 字段白名单 — 仅允许修改 nickname/avatar/gender/bio
     * 防止用户越权修改 phone/email/password/status/balance 等敏感字段
     */
    @PutMapping("/profile")
    public Result<?> updateProfile(@RequestBody User user) {
        long userId = StpUtil.getLoginIdAsLong();
        // 构建仅包含安全字段的更新对象
        User safe = new User();
        safe.setId(userId);
        safe.setNickname(user.getNickname());
        safe.setAvatar(user.getAvatar());
        safe.setGender(user.getGender());
        // bio 字段暂不存在于 User 实体，添加后可使用
        // safe.setBio(user.getBio());
        userService.updateById(safe);
        return Result.ok("更新成功");
    }

    // ==================== Orders ====================

    /**
     * 查询用户在某演出下的剩余购买配额
     * GET /api/user/shows/{showId}/buy-quota
     */
    @GetMapping("/shows/{showId}/buy-quota")
    public Result<?> getBuyQuota(@PathVariable Long showId) {
        long userId = StpUtil.getLoginIdAsLong();
        return orderService.getBuyQuota(userId, showId);
    }

    @PostMapping("/orders")
    public Result<?> createOrder(@Valid @RequestBody OrderCreateDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        return orderService.createOrder(userId, dto);
    }

    /**
     * MQ 异步下单结果查询（前端轮询）
     * GET /api/user/orders/create-status?requestId=xxx
     *
     * 下单接口返回 pending=true 后，前端凭 requestId 轮询此接口，
     * 等待 MQ 消费者落库完成后拿到 orderId 再跳转支付页。
     * Redis Key 中拼入 userId，防止跨用户探测他人下单结果。
     */
    @GetMapping("/orders/create-status")
    public Result<?> getOrderCreateStatus(@RequestParam String requestId) {
        long userId = StpUtil.getLoginIdAsLong();
        Object val = redisTemplate.opsForValue()
                .get(com.livetix.common.constant.RedisKey.ORDER_RESULT + userId + ":" + requestId);
        if (val == null) {
            return Result.ok(Map.of("status", "pending"));
        }
        String s = val.toString();
        if (s.startsWith("OK:")) {
            return Result.ok(Map.of("status", "success", "orderId", Long.parseLong(s.substring(3))));
        }
        return Result.ok(Map.of("status", "fail",
                "reason", s.startsWith("FAIL:") ? s.substring(5) : s));
    }

    @GetMapping("/orders")
    public Result<?> getMyOrders(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status) {
        long userId = StpUtil.getLoginIdAsLong();
        return orderService.getMyOrders(userId, page, pageSize, status);
    }

    /**
     * 获取订单详情（带用户归属校验，防止ID枚举越权访问）
     */
    @GetMapping("/orders/{id}")
    public Result<?> getOrderDetail(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        var order = orderService.getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail("订单不存在");
        }
        return Result.ok(order);
    }

    @PutMapping("/orders/{id}/cancel")
    public Result<?> cancelOrder(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        return orderService.cancelOrder(userId, id);
    }

    @GetMapping("/orders/{id}/pay-status")
    public Result<?> getPayStatus(@PathVariable Long id) {
        var order = orderService.getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        // 实时同步后端超时状态
        long remaining = 0;
        if ("pending".equals(order.getStatus()) && order.getPayExpireTime() != null) {
            remaining = java.time.Duration.between(java.time.LocalDateTime.now(), order.getPayExpireTime()).getSeconds();
            if (remaining < 0) remaining = 0;
            // 如果已超时但状态未更新，主动触发状态同步
            if (remaining == 0) {
                orderService.cancelTimeoutOrders();
            }
        }
        return Result.ok(Map.of("status", order.getStatus(), "remainingSeconds", remaining));
    }

    // ==================== Refund ====================

    @PutMapping("/orders/{id}/refund")
    public Result<?> applyRefund(@PathVariable Long id, @RequestBody Map<String, String> body) {
        long userId = StpUtil.getLoginIdAsLong();
        return refundService.applyRefund(userId, id, body.getOrDefault("reason", ""));
    }

    // ==================== Real Name Info ====================

    @GetMapping("/real-names")
    public Result<?> getRealNames() {
        return realNameService.listMyRealNames();
    }

    @PostMapping("/real-names")
    public Result<?> addRealName(@RequestBody RealNameInfo info) {
        return realNameService.addRealName(info);
    }

    @PutMapping("/real-names/{id}")
    public Result<?> updateRealName(@PathVariable Long id, @RequestBody RealNameInfo info) {
        return realNameService.updateRealName(id, info);
    }

    @DeleteMapping("/real-names/{id}")
    public Result<?> deleteRealName(@PathVariable Long id) {
        return realNameService.deleteRealName(id);
    }

    // ==================== Favorites ====================

    @GetMapping("/favorites")
    public Result<?> getFavorites() {
        return favoriteService.getMyFavorites();
    }

    @PostMapping("/favorites/{showId}")
    public Result<?> addFavorite(@PathVariable Long showId) {
        return favoriteService.addFavorite(showId);
    }

    @DeleteMapping("/favorites/{showId}")
    public Result<?> removeFavorite(@PathVariable Long showId) {
        return favoriteService.removeFavorite(showId);
    }

    // ==================== Reminders ====================

    @PostMapping("/reminders/{showId}")
    public Result<?> setReminder(@PathVariable Long showId) {
        return reminderService.setReminder(showId);
    }

    @DeleteMapping("/reminders/{showId}")
    public Result<?> cancelReminder(@PathVariable Long showId) {
        return reminderService.cancelReminder(showId);
    }

    @GetMapping("/reminders")
    public Result<?> getReminders() {
        return reminderService.getMyReminders();
    }

    // ==================== Notifications ====================

    @GetMapping("/notifications")
    public Result<?> getNotifications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String type) {
        return notificationService.getUserNotifications(page, pageSize, type);
    }

    @PutMapping("/notifications/{id}/read")
    public Result<?> markRead(@PathVariable Long id) {
        return notificationService.markAsRead(id);
    }

    @PutMapping("/notifications/read-all")
    public Result<?> markAllRead() {
        return notificationService.markAllAsRead();
    }

    @GetMapping("/notifications/unread-count")
    public Result<?> getUnreadCount() {
        return notificationService.getUnreadCount();
    }

    // ==================== Wallet ====================

    @GetMapping("/wallet")
    public Result<?> getWallet() {
        return walletService.getBalance();
    }

    @GetMapping("/wallet/transactions")
    public Result<?> getTransactions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return walletService.getTransactions(page, pageSize);
    }

    /**
     * 39 修复: 不存在的充值接口 — 生产环境删除或接入真实支付
     * 当前仅供管理员在后台使用，普通用户需走真实支付网关充值
     */
    @PostMapping("/wallet/recharge")
    @cn.dev33.satoken.annotation.SaCheckRole("admin")
    public Result<?> recharge(@RequestBody Map<String, BigDecimal> body) {
        return walletService.recharge(body.get("amount"));
    }

    // ==================== Security ====================

    @PostMapping("/change-password")
    public Result<?> changePassword(@RequestBody Map<String, String> body) {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        String oldPwd = body.get("oldPassword");
        if (oldPwd == null || !BCrypt.checkpw(oldPwd, user.getPassword())) {
            return Result.fail("旧密码不正确");
        }
        String newPwd = body.get("password");
        if (newPwd == null || newPwd.length() < 6) {
            return Result.fail("密码至少6位");
        }
        user.setPassword(BCrypt.hashpw(newPwd, BCrypt.gensalt(10)));
        userService.updateById(user);
        StpUtil.logout(userId);
        return Result.ok("密码修改成功");
    }

    // ==================== Bind Phone / Email ====================

    @PutMapping("/bind-phone")
    public Result<?> bindPhone(@RequestBody Map<String, String> body) {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(userId);
        String code = body.get("code");
        String phone = body.get("phone");
        if (code == null || phone == null) {
            return Result.fail("参数不完整");
        }

        // 校验验证码
        String codeKey = VERIFY_CODE_KEY + phone;
        Object storedCode = redisTemplate.opsForValue().get(codeKey);
        if (storedCode == null || !storedCode.toString().equals(code)) {
            return Result.fail("验证码错误或已过期");
        }
        redisTemplate.delete(codeKey);

        user.setPhone(phone);
        userService.updateById(user);
        return Result.ok("手机号绑定成功");
    }

    @PutMapping("/bind-email")
    public Result<?> bindEmail(@RequestBody Map<String, String> body) {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(userId);
        String code = body.get("code");
        String email = body.get("email");
        if (code == null || email == null) {
            return Result.fail("参数不完整");
        }

        // 校验验证码
        String codeKey = VERIFY_CODE_KEY + email;
        Object storedCode = redisTemplate.opsForValue().get(codeKey);
        if (storedCode == null || !storedCode.toString().equals(code)) {
            return Result.fail("验证码错误或已过期");
        }
        redisTemplate.delete(codeKey);

        user.setEmail(email);
        userService.updateById(user);
        return Result.ok("邮箱绑定成功");
    }
}
