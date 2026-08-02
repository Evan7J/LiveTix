package com.livetix.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livetix.common.Result;
import com.livetix.dto.LoginDTO;
import com.livetix.dto.RegisterDTO;
import com.livetix.entity.Banner;
import com.livetix.entity.Category;
import com.livetix.entity.User;
import com.livetix.mapper.BannerMapper;
import com.livetix.mapper.CategoryMapper;
import com.livetix.mapper.OrderMapper;
import com.livetix.entity.Order;
import com.livetix.service.OrderService;
import com.livetix.service.ProductService;
import com.livetix.service.ShowService;
import com.livetix.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Public APIs — no login required
 *
 * ============================================================
 * 安全加固：
 *   1. 登录接口添加 Redis 滑动窗口限流
 *   2. 支付回调添加签名验证 + 金额校验
 *   3. 注册接口使用 RegisterDTO 隔离敏感字段
 *   4. 分页参数上限校验
 * ============================================================
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final UserService userService;

    private final ShowService showService;

    private final BannerMapper bannerMapper;

    private final CategoryMapper categoryMapper;

    private final OrderMapper orderMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    private final OrderService orderService;

    private final ProductService productService;

    /** 支付回调签名密钥（生产环境从配置中心读取） */
    @Value("${payment.callback-secret:livetix_pay_callback_secret_2026}")
    private String callbackSecret;

    /** 登录限流：每分钟最大尝试次数 */
    private static final int LOGIN_RATE_LIMIT = 5;
    /** 限流窗口大小（秒）*/
    private static final long RATE_LIMIT_WINDOW_SECONDS = 60;
    /** 分页最大值 */
    private static final int MAX_PAGE_SIZE = 100;
    /** Redis Key 前缀 */
    private static final String LOGIN_LIMIT_IP_KEY = "livetix:login:limit:ip:";
    private static final String LOGIN_LIMIT_USER_KEY = "livetix:login:limit:user:";
    private static final String VERIFY_CODE_KEY = "livetix:verify:code:";

    // ==================== Auth ====================

    /**
     * 用户登录
     * 安全措施：
     *   - 滑动窗口限流（IP + 用户名双维度）
     *   - 统一返回"用户名或密码错误"，防止撞库攻击枚举有效用户名
     */
    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String username = dto.getUsername();

        // 检查 IP 限流
        if (!checkRateLimit(LOGIN_LIMIT_IP_KEY + clientIp)) {
            return Result.fail("操作过于频繁，请1分钟后再试");
        }

        // 检查用户名限流
        if (!checkRateLimit(LOGIN_LIMIT_USER_KEY + username)) {
            return Result.fail("该账号登录尝试过多，请1分钟后再试");
        }

        // 执行登录
        Result<?> result = userService.login(dto);

        // 登录成功后清除限流记录
        if (result.getCode() == 200) {
            clearRateLimit(LOGIN_LIMIT_IP_KEY + clientIp);
            clearRateLimit(LOGIN_LIMIT_USER_KEY + username);
        }

        return result;
    }

    /**
     * 用户注册
     * 安全措施：使用 RegisterDTO 隔离敏感字段，后端强制设置 role='user'
     */
    /**
     * 41 修复: 注册接口加 IP 限流 — 每小时最多 3 次注册
     */
    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterDTO dto, jakarta.servlet.http.HttpServletRequest request) {
        // 41: IP 限流 — 每小时每个 IP 最多注册 3 次
        String ip = getClientIp(request);
        String regLimitKey = "livetix:reg:limit:" + ip;
        Long regCount = redisTemplate.opsForValue().increment(regLimitKey);
        if (regCount != null && regCount == 1) {
            redisTemplate.expire(regLimitKey, 1, TimeUnit.HOURS);
        }
        if (regCount != null && regCount > 3) {
            return Result.fail("注册过于频繁，请稍后再试");
        }

        // 如果提供了验证码，先校验
        if (dto.getCode() != null && !dto.getCode().isBlank()) {
            // 验证码校验（可通过手机号或邮箱查找）
            String codeKey = VERIFY_CODE_KEY + (dto.getPhone() != null ? dto.getPhone() : dto.getEmail());
            Object storedCode = redisTemplate.opsForValue().get(codeKey);
            if (storedCode == null || !storedCode.toString().equals(dto.getCode())) {
                return Result.fail("验证码错误或已过期");
            }
            // 校验通过后删除验证码
            redisTemplate.delete(codeKey);
        }

        // 将 RegisterDTO 转换为 User 实体
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword());
        user.setNickname(dto.getNickname());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setGender(dto.getGender());

        return userService.register(user);
    }

    /**
     * 发送验证码
     * 将验证码存储到 Redis，TTL 5分钟
     */
    /**
     * 42 修复: 发送验证码 — IP限流 + 目标限流 + 发送间隔
     */
    @PostMapping("/send-code")
    public Result<?> sendVerifyCode(@RequestBody Map<String, String> body,
                                    jakarta.servlet.http.HttpServletRequest request) {
        String target = body.get("target"); // 手机号或邮箱
        String type = body.get("type");     // phone / email
        if (target == null || type == null) {
            return Result.fail("参数不正确");
        }

        String ip = getClientIp(request);

        // 42: IP 限流 — 每小时每个 IP 最多 10 次发码
        String ipLimitKey = "livetix:code:ip:" + ip;
        Long ipCount = redisTemplate.opsForValue().increment(ipLimitKey);
        if (ipCount != null && ipCount == 1) redisTemplate.expire(ipLimitKey, 1, TimeUnit.HOURS);
        if (ipCount != null && ipCount > 10) {
            return Result.fail("发送过于频繁，请稍后再试");
        }

        // 42: 目标限流 — 同一手机号/邮箱 60s 内只能发一次
        String intervalKey = "livetix:code:interval:" + target;
        Boolean hasInterval = redisTemplate.hasKey(intervalKey);
        if (Boolean.TRUE.equals(hasInterval)) {
            return Result.fail("验证码已发送，请60秒后再试");
        }

        // 生成6位数字验证码
        String code = String.format("%06d", (int)(Math.random() * 1000000));
        String codeKey = VERIFY_CODE_KEY + target;

        // 存储验证码，5分钟过期
        redisTemplate.opsForValue().set(codeKey, code, 5, TimeUnit.MINUTES);
        // 42: 设置发送间隔标记，60s
        redisTemplate.opsForValue().set(intervalKey, "1", 60, TimeUnit.SECONDS);

        // TODO: 集成短信/邮件服务发送真实验证码
        System.out.println("========================================");
        System.out.println("  验证码 [" + target + "]: " + code);
        System.out.println("========================================");

        return Result.ok("验证码已发送（有效期5分钟）");
    }

    // ==================== Banners ====================

    @GetMapping("/banners")
    public Result<?> banners() {
        List<Banner> banners = bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, 1)
                        .orderByAsc(Banner::getSort));
        return Result.ok(banners);
    }

    // ==================== Categories ====================

    @GetMapping("/categories")
    public Result<?> categories() {
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, 1)
                        .orderByAsc(Category::getSort));
        return Result.ok(categories);
    }

    // ==================== Products (校园闲置交易) ====================

    /**
     * 商品列表（公开接口）
     * GET /api/public/products?page=1&pageSize=10&categoryId=1&keyword=手机&sort=newest
     */
    @GetMapping("/products")
    public Result<?> listProducts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort) {
        if (pageSize > MAX_PAGE_SIZE) pageSize = MAX_PAGE_SIZE;
        if (page < 1) page = 1;
        return productService.listProducts(page, pageSize, categoryId, keyword, sort);
    }

    /**
     * 商品详情（公开接口）
     * GET /api/public/products/{id}
     */
    @GetMapping("/products/{id}")
    public Result<?> productDetail(@PathVariable Long id) {
        return productService.getProductDetail(id);
    }

    // ==================== Shows ====================

    @GetMapping("/shows/hot")
    public Result<?> hotShows() {
        return showService.getHotShows();
    }

    @GetMapping("/shows")
    public Result<?> listShows(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String sort) {
        // 安全加固：分页大小上限校验，防止内存溢出攻击
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        if (page < 1) {
            page = 1;
        }
        return showService.listShows(page, pageSize, categoryId, 1, keyword, city, timeRange, date, sort);
    }

    @GetMapping("/shows/{id}")
    public Result<?> showDetail(@PathVariable Long id) {
        return showService.getShowDetail(id);
    }

    @GetMapping("/shows/search")
    public Result<?> searchShows(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long categoryId) {
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        return showService.searchShows(keyword, page, pageSize, categoryId);
    }

    /**
     * 获取指定演出的已售座位坐标列表
     * 返回的坐标格式为 "r-c"（行-列），与管理员设置的 cells 数据格式一致
     */
    /**
     * 38 修复: 已售座位缓存 + 分页限制，避免全表扫描
     */
    @GetMapping("/shows/{id}/sold-cells")
    public Result<?> soldCells(@PathVariable Long id) {
        // 38: 先从 Redis 缓存读取（TTL 30s，减少 DB 查询频率）
        String cacheKey = "livetix:sold:cells:" + id;
        @SuppressWarnings("unchecked")
        java.util.List<String> cached = (java.util.List<String>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Result.ok(cached);
        }

        // 38: 加 LIMIT 200 防止大结果集，用 last("LIMIT 200") 限制返回
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getShowId, id)
                        .in(Order::getStatus, "paid", "pending")
                        .isNotNull(Order::getSeats)
                        .ne(Order::getSeats, "")
                        .last("LIMIT 200"));
        // 收集所有座位坐标
        java.util.Set<String> soldCells = new java.util.LinkedHashSet<>();
        for (Order order : orders) {
            String seats = order.getSeats();
            if (seats != null && !seats.isBlank()) {
                for (String part : seats.split("[，,|]")) {
                    part = part.trim();
                    if (part.matches("\\d+-\\d+")) {
                        soldCells.add(part);
                    }
                }
            }
        }
        List<String> result = List.copyOf(soldCells);
        // 38: 缓存 30 秒，减轻 DB 压力
        redisTemplate.opsForValue().set(cacheKey, result, 30, TimeUnit.SECONDS);
        return Result.ok(result);
    }

    // ==================== Payment Callback ====================

    /**
     * 支付异步回调接口（由支付平台调用，无需认证）
     *
     * POST /api/public/pay/callback
     *
     * 安全措施：
     *   1. 验证回调签名（防篡改）
     *   2. 验证订单金额一致性（防金额篡改）
     *   3. 幂等处理防止重复通知
     *   4. IP白名单校验（生产环境启用）
     */
    @PostMapping("/pay/callback")
    public Result<?> payCallback(@RequestBody Map<String, String> params) {
        String orderNo = params.get("out_trade_no");
        String transactionId = params.get("transaction_id");
        String payAmount = params.get("total_amount");
        String sign = params.get("sign");

        if (orderNo == null || transactionId == null) {
            return Result.fail("参数错误");
        }

        // ==========================================
        // 1. 验证回调签名（防篡改）
        // ==========================================
        if (!verifyCallbackSign(params, sign)) {
            return Result.fail("签名验证失败");
        }

        // ==========================================
        // 2. 查询订单
        // ==========================================
        var order = orderService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.livetix.entity.Order>()
                        .eq(com.livetix.entity.Order::getOrderNo, orderNo));
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // ==========================================
        // 3. 幂等处理：已支付的订单不再重复处理
        // ==========================================
        if ("paid".equals(order.getStatus())) {
            return Result.ok("OK");
        }
        if (!"pending".equals(order.getStatus())) {
            return Result.fail("订单状态异常");
        }

        // ==========================================
        // 4. 验证支付金额是否匹配
        // ==========================================
        if (payAmount != null && !payAmount.isBlank()) {
            BigDecimal callbackAmount = new BigDecimal(payAmount);
            if (callbackAmount.compareTo(order.getPayAmount()) != 0) {
                return Result.fail("支付金额不匹配");
            }
        }

        // ==========================================
        // 5. 更新订单状态
        // ==========================================
        orderService.processPayment(orderNo, "wechat");
        return Result.ok("OK");
    }

    /**
     * 验证支付回调签名
     *
     * 签名算法（HMAC-SHA256）:
     *   1. 除 sign 字段外的所有参数按 key 字典序排序
     *   2. 拼接成 key1=value1&key2=value2 格式
     *   3. 使用回调密钥进行 HMAC-SHA256 签名
     *   4. 与传入的 sign 对比
     *
     * 对接微信支付时替换为：
     *   - 微信: 使用微信提供的 SDK (WXPayUtility) 验证
     *   - 支付宝: 使用 AlipaySignature.rsaCheckV1 验证
     */
    private boolean verifyCallbackSign(Map<String, String> params, String sign) {
        if (sign == null || sign.isBlank()) {
            return false;
        }
        try {
            // 排除 sign 字段，按 key 字典序排序
            StringBuilder sb = new StringBuilder();
            params.entrySet().stream()
                    .filter(e -> !"sign".equals(e.getKey()) && e.getValue() != null)
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        if (sb.length() > 0) sb.append("&");
                        sb.append(e.getKey()).append("=").append(e.getValue());
                    });

            // HMAC-SHA256 签名
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    callbackSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            String computedSign = bytesToHex(hash);

            // 使用时间安全的比较方式
            return MessageDigest.isEqual(
                    computedSign.getBytes(StandardCharsets.UTF_8),
                    sign.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==================== Rate Limiting Helpers ====================

    /**
     * 滑动窗口限流检查
     */
    private boolean checkRateLimit(String redisKey) {
        try {
            long now = System.currentTimeMillis();
            long windowStart = now - RATE_LIMIT_WINDOW_SECONDS * 1000;

            redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);
            Long count = redisTemplate.opsForZSet().zCard(redisKey);

            if (count != null && count >= LOGIN_RATE_LIMIT) {
                return false;
            }

            double score = now + Math.random();
            redisTemplate.opsForZSet().add(redisKey, String.valueOf(now), score);
            redisTemplate.expire(redisKey, RATE_LIMIT_WINDOW_SECONDS * 2, TimeUnit.SECONDS);

            return true;
        } catch (Exception e) {
            // 37 修复: Redis 异常时拒绝请求（fail-closed），防止暴力破解
            return false;
        }
    }

    private void clearRateLimit(String redisKey) {
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception ignored) {
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
