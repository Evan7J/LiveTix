package com.livetix.demo;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo 8-3: 限流 + 防重 + 幂等——三层防护，各司其职
 *
 * ============================================================
 * 面试场景还原：
 * ============================================================
 *
 * 面试官："你们的系统怎么防止用户重复下单？"
 *
 * 初级回答（❌）：
 *   "我们做了限流，每秒只允许 100 个请求。"
 *   → 面试官内心：限流和防重是两个概念，混淆了。
 *
 * 标准回答（✅）：
 *   "我们做了三层防护：
 *    1. 令牌桶限流：系统层面，每秒放行 500 个请求，保护整体不被打垮。
 *    2. 用户防重锁：用户层面，下单后 10 秒内不能重复提交，
 *       用 Redis SET NX 实现，防止用户手抖多点。
 *    3. 幂等校验：业务层面，用 requestId 做唯一标识，
 *       同一个 requestId 的请求只处理一次，防止网络重传导致重复下单。"
 *
 * ============================================================
 * 三个概念的区别（面试必考）：
 * ============================================================
 *
 * 限流（Rate Limiting）：
 *   - 问题: 10000 人同时抢，系统扛不住
 *   - 解决: 只放行 500 人/秒，其余直接拒绝
 *   - 粒度: 全局（不管你是谁，只看令牌够不够）
 *   - 实现: 令牌桶 / 滑动窗口 / 漏桶
 *
 * 防重（Anti-Duplicate）：
 *   - 问题: 用户手抖点了两次，生成了两个订单
 *   - 解决: 第一次下单后加锁 10 秒，10 秒内同一用户同场次不能再次下单
 *   - 粒度: 用户 + 场次 级别
 *   - 实现: Redis SET NX EX 10
 *
 * 幂等（Idempotency）：
 *   - 问题: 网络超时，用户不知道下单成功没，重试了一次
 *   - 解决: 每次请求带 requestId，后端检查是否已处理过
 *   - 粒度: 单次请求 级别
 *   - 实现: Redis SET NX（requestId） / DB 唯一索引（order_no）
 *
 * 三者关系：一个请求先过限流（系统层面），再过防重（用户层面），
 *          最后过幂等（请求层面）。层层递进，粒度越来越细。
 */
public class RateLimitAndDedupDemo {

    // ========== 模拟数据库 ==========
    private static final Set<String> processedRequestIds = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger orderCreated = new AtomicInteger(0);
    private static final AtomicInteger duplicateBlocked = new AtomicInteger(0);
    private static final AtomicInteger idempotentBlocked = new AtomicInteger(0);
    private static final AtomicInteger rateLimited = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║     Demo 8-3: 限流 + 防重 + 幂等——三层防护                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        // ===== 场景 1: 三种机制的对比演示 =====
        testThreeMechanisms();

        // ===== 场景 2: 限流算法对比 =====
        testRateLimitAlgorithms();

        System.out.println("\n========== Demo 8-3 完成 ==========");
    }

    /**
     * 场景 1: 模拟一个用户下单的完整流程，展示三层防护
     *
     * 模拟场景：用户 userId=1001 下单 showId=1，requestId="req-abc-123"
     *
     * 请求 1: 正常下单 → 成功
     * 请求 2: 同一用户 2 秒后再次下单 → 防重拦截
     * 请求 3: 同一 requestId 重试 → 幂等拦截
     * 请求 4: 10 秒后防重锁过期，重新下单 → 成功
     */
    private static void testThreeMechanisms() throws InterruptedException {
        System.out.println("═".repeat(60));
        System.out.println("  场景 1: 三层防护——同一个用户的各种下单场景");
        System.out.println("═".repeat(60) + "\n");

        long userId = 1001L;
        long showId = 1L;
        String requestId = "req-abc-123";

        // 用户防重锁：userId → 锁定时间戳
        Map<Long, Long> userLock = new ConcurrentHashMap<>();

        // ── 请求 1: 正常下单 ──
        System.out.println("请求 1: 用户" + userId + " 首次下单, requestId=" + requestId);
        String result = processOrder(userId, showId, requestId, userLock);
        System.out.println("  结果: " + result + "\n");

        // ── 请求 2: 2 秒后再次下单（防重拦截）──
        Thread.sleep(100); // 模拟 2 秒
        System.out.println("请求 2: 用户" + userId + " 手抖又点了一次（2秒后）");
        result = processOrder(userId, showId, "req-xyz-456", userLock);
        System.out.println("  结果: " + result);
        System.out.println("  解释: 防重锁拦截！10 秒内同一用户不能重复下单\n");

        // ── 请求 3: 同一 requestId 重试（幂等拦截）──
        Thread.sleep(100);
        System.out.println("请求 3: 网络超时，用户用同一个 requestId 重试");
        result = processOrder(userId, showId, requestId, userLock);
        System.out.println("  结果: " + result);
        System.out.println("  解释: 幂等拦截！requestId 已处理过，直接返回上次结果\n");

        // ── 请求 4: 10 秒后防重锁过期，重新下单 ──
        Thread.sleep(100);
        // 模拟 10 秒后：手动清除防重锁
        userLock.remove(userId);
        System.out.println("请求 4: 10 秒后，用户重新下单, requestId=req-new-789");
        result = processOrder(userId, showId, "req-new-789", userLock);
        System.out.println("  结果: " + result);
        System.out.println("  解释: 防重锁已过期，新 requestId，正常下单\n");

        System.out.println("  📊 统计:");
        System.out.println("    下单成功:    " + orderCreated.get());
        System.out.println("    防重拦截:    " + duplicateBlocked.get());
        System.out.println("    幂等拦截:    " + idempotentBlocked.get());
        System.out.println("    限流拦截:    " + rateLimited.get());
    }

    /**
     * 处理下单请求（集成了防重 + 幂等）
     *
     * 这个方法的逻辑顺序就是面试要讲清楚的：
     *   1. 先检查幂等（requestId 是否处理过）→ 处理过直接返回
     *   2. 再检查防重（用户是否在锁定时间内）→ 锁定中拒绝
     *   3. 都没问题 → 执行业务 + 记录幂等 + 加防重锁
     */
    private static String processOrder(long userId, long showId, String requestId,
                                       Map<Long, Long> userLock) {
        // 第 1 步：幂等检查（requestId 是否已处理）
        // 真实实现：Redis SET NX，key = "order:dedup:" + requestId
        if (processedRequestIds.contains(requestId)) {
            idempotentBlocked.incrementAndGet();
            return "IDEMPOTENT_BLOCKED: 请求已处理，请勿重复提交";
        }

        // 第 2 步：防重检查（用户是否在锁定时间内）
        // 真实实现：Redis SET user:order:lock:{userId}:{showId} 1 EX 10 NX
        long now = System.currentTimeMillis();
        Long existingLock = userLock.putIfAbsent(userId, now);
        if (existingLock != null && (now - existingLock < 10_000)) {
            duplicateBlocked.incrementAndGet();
            return "DUPLICATE_BLOCKED: 操作太频繁，请 10 秒后再试";
        }
        // 如果锁过期了，替换为新锁
        if (existingLock != null) {
            userLock.put(userId, now);
        }

        // 第 3 步：执行业务（创建订单）
        processedRequestIds.add(requestId);  // 记录幂等
        orderCreated.incrementAndGet();
        return "SUCCESS: 订单创建成功";
    }

    /**
     * 场景 2: 三种限流算法直观对比
     *
     * 固定窗口: 简单但有边界问题
     * 滑动窗口: 精确但内存占用大
     * 令牌桶:   平滑，允许突发流量
     */
    private static void testRateLimitAlgorithms() {
        System.out.println("═".repeat(60));
        System.out.println("  场景 2: 三种限流算法对比");
        System.out.println("═".repeat(60) + "\n");

        System.out.println("  假设限制: 每秒最多 2 个请求\n");

        System.out.println("  ┌──────────────┬──────────────────────────────────────┐");
        System.out.println("  │ 算法          │ 时间线: 0.0s  0.5s  0.9s  1.0s  1.1s │");
        System.out.println("  ├──────────────┼──────────────────────────────────────┤");
        System.out.println("  │ 固定窗口      │  ✅    ✅    ❌    ✅    ✅          │");
        System.out.println("  │ 0-1s窗口:2个  │  ↑ 0.9s 的请求被拒，但 1.1s 的请求  │");
        System.out.println("  │ 1-2s窗口:2个  │  在 0.3s 内来了 4 个请求（边界问题） │");
        System.out.println("  ├──────────────┼──────────────────────────────────────┤");
        System.out.println("  │ 滑动窗口      │  ✅    ✅    ❌    ❌    ✅          │");
        System.out.println("  │ 过去1s最多2个 │  ↑ 0.9s 被拒, 1.0s 被拒（窗口内有  │");
        System.out.println("  │               │  0.5s 和 0.0s 的请求）              │");
        System.out.println("  ├──────────────┼──────────────────────────────────────┤");
        System.out.println("  │ 令牌桶        │  ✅    ✅    ❌    ❌    ❌          │");
        System.out.println("  │ 容量2, 每秒2个│  ↑ 0.9s 时桶空, 1.0s 补充 2 个令牌但 │");
        System.out.println("  │               │  之前没剩，只能等下一秒补充           │");
        System.out.println("  └──────────────┴──────────────────────────────────────┘\n");

        System.out.println("  固定窗口的边界问题（面试必考）：");
        System.out.println("    窗口 0.0s-1.0s: 允许 2 个请求");
        System.out.println("    窗口 1.0s-2.0s: 允许 2 个请求");
        System.out.println("    如果 0.9s 来 2 个请求，1.1s 来 2 个请求，");
        System.out.println("    → 0.2s 内实际来了 4 个请求！两个窗口『各管各的』，都合法。\n");

        System.out.println("  LiveTix 项目选择：");
        System.out.println("    - 注册/登录接口: 滑动窗口（安全优先，用户不多）");
        System.out.println("    - 秒杀接口:     令牌桶（性能优先，允许突发流量）");
    }
}