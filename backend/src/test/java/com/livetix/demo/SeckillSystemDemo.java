package com.livetix.demo;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demo 8-1: 完整秒杀流程模拟——从用户请求到订单落库的全链路
 *
 * ============================================================
 * 一、这个 Demo 做了什么？
 * ============================================================
 *
 * 模拟 10000 个用户同时抢购 100 张演唱会门票，展示完整的秒杀系统防护链路：
 *
 *   用户请求
 *     │
 *     ├── 第1关：令牌桶限流            → 超限直接拒绝（429）
 *     ├── 第2关：缓存穿透防护          → 不存在的演出，NULL 占位
 *     ├── 第3关：Redis 预扣库存        → Lua 原子操作，库存不足返回售罄
 *     ├── 第4关：用户防重锁            → 同一用户 10 秒内不能重复下单
 *     ├── 第5关：MQ 异步下单            → 削峰填谷，用户立即拿到响应
 *     └── 第6关：DB 乐观锁写入          → 最终防线，WHERE stock >= quantity
 *
 * ============================================================
 * 二、和 LiveTix 真实项目的对应关系
 * ============================================================
 *
 * 模拟组件                    真实项目对应
 * ─────────────────────────────────────────────────
 * tokenBucket                 token_bucket.lua + RateLimiterUtil
 * showCache (ConcurrentHashMap) Redis + ShowService.getShowById()
 * redisStock (AtomicInteger)   stock_deduct.lua + RedisTemplate
 * userLock (ConcurrentHashMap) Redis SET NX (livetix:user:order:lock:)
 * orderQueue (BlockingQueue)   RocketMQ (livetix-order-topic)
 * dbStock (AtomicInteger+CAS)  ShowMapper.deductStock() SQL乐观锁
 * threadPool                   Spring @Async 线程池
 *
 * ============================================================
 * 三、面试话术：如何一句话讲清楚这个系统？
 * ============================================================
 *
 * "我的秒杀系统用 Redis Lua 脚本做原子预扣库存，拦截 99% 无效请求；
 *  用 RocketMQ 异步下单实现削峰填谷，用户 50ms 内拿到响应；
 *  用 SQL WHERE 条件做乐观锁，作为超卖的最后一道防线；
 *  同时做了缓存穿透（NULL 占位）、缓存击穿（互斥锁）、缓存雪崩（TTL 随机）三层保护。"
 */
public class SeckillSystemDemo {

    // ==================== 系统配置 ====================
    private static final int TOTAL_STOCK = 100;          // 总库存：100 张票
    private static final int USER_COUNT = 10000;         // 抢票用户数：10000 人
    private static final int TOKEN_BUCKET_CAPACITY = 500; // 令牌桶容量：500
    private static final int TOKEN_REFILL_RATE = 200;    // 每秒补充令牌数：200

    // ==================== 第一关：令牌桶限流 ====================
    private static final AtomicInteger tokenBucket = new AtomicInteger(TOKEN_BUCKET_CAPACITY);
    private static final AtomicLong lastTokenRefillTime = new AtomicLong(System.currentTimeMillis());
    private static final AtomicInteger rateLimitRejectCount = new AtomicInteger(0);

    // ==================== 第二关：缓存（模拟 Redis） ====================
    // showId → 演出信息（含库存），模拟 Redis 缓存
    private static final ConcurrentHashMap<Long, ShowInfo> showCache = new ConcurrentHashMap<>();
    // 缓存空值占位（防穿透）
    private static final Set<Long> nullCache = ConcurrentHashMap.newKeySet();
    // 缓存重建互斥锁（防击穿）
    private static final ConcurrentHashMap<Long, Boolean> cacheRebuildLock = new ConcurrentHashMap<>();

    // ==================== 第三关：Redis 预扣库存（模拟） ====================
    // 用 AtomicInteger 模拟 Redis 中的库存计数器
    // 真实项目用 Lua 脚本 DECR + 判断，这里用 CAS 模拟原子性
    private static final AtomicInteger redisStock = new AtomicInteger(TOTAL_STOCK);

    // ==================== 第四关：用户防重锁 ====================
    // userId → 锁定时间戳，模拟 Redis SET NX EX 10
    private static final ConcurrentHashMap<Long, Long> userOrderLock = new ConcurrentHashMap<>();

    // ==================== 第五关：MQ 消息队列（模拟） ====================
    private static final BlockingQueue<OrderMessage> orderQueue = new LinkedBlockingQueue<>(1000);
    private static final AtomicInteger mqSendSuccess = new AtomicInteger(0);
    private static final AtomicInteger mqSendFail = new AtomicInteger(0);

    // ==================== 第六关：DB 乐观锁（模拟） ====================
    private static final AtomicInteger dbStock = new AtomicInteger(TOTAL_STOCK);
    private static final AtomicInteger dbWriteSuccess = new AtomicInteger(0);
    private static final AtomicInteger dbWriteFail = new AtomicInteger(0);

    // ==================== 统计计数器 ====================
    private static final AtomicInteger orderSuccess = new AtomicInteger(0);  // 最终下单成功
    private static final AtomicInteger soldOutResponse = new AtomicInteger(0); // 返回售罄
    private static final AtomicInteger requestId = new AtomicInteger(0);

    // ==================== 线程池 ====================
    // 模拟 Tomcat 线程池处理用户请求
    private static final ThreadPoolExecutor requestPool = new ThreadPoolExecutor(
            50, 100, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2000),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // MQ 消费者线程池（模拟 RocketMQ 消费）
    private static final ExecutorService consumerPool = Executors.newFixedThreadPool(10);

    // ==================== 定时任务：令牌桶补充 ====================
    private static final ScheduledExecutorService tokenRefiller = Executors.newSingleThreadScheduledExecutor();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        Demo 8-1: 完整秒杀系统全链路模拟                       ║");
        System.out.println("║        10000 人抢 100 张票，六道防线层层保护                   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        // 初始化：把演出信息加载到缓存（模拟 Redis 预热）
        initShowCache();

        // 启动令牌桶补充定时任务（每秒补充 200 个令牌）
        startTokenRefiller();

        // 启动 MQ 消费者（10 个线程慢慢消费，模拟削峰填谷）
        startMqConsumers();

        // 开始压测
        long startTime = System.currentTimeMillis();
        System.out.println("🚀 开始秒杀！" + USER_COUNT + " 个用户同时抢购...\n");

        CountDownLatch allDone = new CountDownLatch(USER_COUNT);

        for (int i = 1; i <= USER_COUNT; i++) {
            final long userId = i;
            requestPool.execute(() -> {
                try {
                    String result = handleSeckillRequest(userId, 1L, 1);
                    // 每 1000 个请求打印一次进度
                    int count = requestId.incrementAndGet();
                    if (count % 1000 == 0) {
                        System.out.println("  [进度] 已处理 " + count + " 个请求, "
                                + "库存剩余: " + redisStock.get()
                                + ", 下单成功: " + orderSuccess.get());
                    }
                } finally {
                    allDone.countDown();
                }
            });
        }

        allDone.await();
        long totalTime = System.currentTimeMillis() - startTime;

        // 等待 MQ 消费者处理完队列中的消息
        System.out.println("\n⏳ 等待 MQ 消费者处理剩余消息...");
        Thread.sleep(3000);

        // 打印最终统计
        printFinalReport(totalTime);

        // 关闭线程池
        requestPool.shutdown();
        consumerPool.shutdown();
        tokenRefiller.shutdown();
        System.out.println("\n========== Demo 8-1 完成 ==========");
    }

    // ================================================================
    //  核心方法：处理一次秒杀请求（六道防线）
    // ================================================================

    /**
     * 处理单个用户的秒杀请求（完整链路）
     *
     * 这就是面试官最想听的：从用户点击到下单成功的完整链路。
     * 每一步都有对应的面试考点，后面会逐条拆解。
     */
    private static String handleSeckillRequest(long userId, long showId, int quantity) {

        // ──── 第 1 关：令牌桶限流 ────
        // 面试考点：为什么用令牌桶而不是固定窗口？
        // 答：固定窗口有边界问题（59秒和00秒的请求都算合法），令牌桶更平滑。
        if (!tryAcquireToken()) {
            rateLimitRejectCount.incrementAndGet();
            return "REJECTED: 系统繁忙，请稍后重试";  // HTTP 429
        }

        // ──── 第 2 关：缓存查询（穿透防护） ────
        // 面试考点：缓存穿透怎么防？
        // 答：不存在的 key 缓存 NULL 占位，TTL 60 秒。
        ShowInfo show = getShowFromCache(showId);
        if (show == null) {
            return "ERROR: 演出不存在";
        }
        if (show.stock <= 0) {
            soldOutResponse.incrementAndGet();
            return "SOLD_OUT: 已售罄";
        }

        // ──── 第 3 关：Redis 预扣库存（Lua 原子操作） ────
        // 面试考点：为什么用 Lua 脚本而不是 Java 代码？
        // 答：GET + SET 在 Java 里是两步，不原子，会超卖。
        //     Lua 脚本在 Redis 单线程中执行，GET、判断、DECR 是原子操作。
        boolean deducted = redisAtomicDeduct(quantity);
        if (!deducted) {
            soldOutResponse.incrementAndGet();
            return "SOLD_OUT: 库存不足";
        }

        // ──── 第 4 关：用户防重锁 ────
        // 面试考点：为什么 10 秒而不是永久？
        // 答：防止用户因网络问题重复提交，10 秒后允许重试（体验好）。
        //     永久锁会导致用户第一次失败后永远无法下单。
        if (!acquireUserLock(userId, showId)) {
            // 锁没拿到说明 10 秒内有重复请求，回滚 Redis 库存
            redisStock.incrementAndGet();
            return "DUPLICATE: 请勿重复提交";
        }

        // ──── 第 5 关：MQ 异步下单（削峰填谷） ────
        // 面试考点：为什么用异步而不是同步？
        // 答：同步写 DB 需要 50-200ms，10000 人就是 500-2000 秒。
        //     异步只做 Redis 扣减 + 发 MQ，< 5ms，用户立即拿到响应。
        OrderMessage msg = new OrderMessage(userId, showId, quantity, System.currentTimeMillis());
        boolean sent = orderQueue.offer(msg);
        if (!sent) {
            // MQ 满了，回滚 Redis 库存 + 释放锁
            redisStock.incrementAndGet();
            releaseUserLock(userId, showId);
            mqSendFail.incrementAndGet();
            return "ERROR: 系统繁忙，请稍后重试";
        }
        mqSendSuccess.incrementAndGet();

        // 用户立即拿到响应！
        // 后面的 DB 写入由 MQ 消费者异步完成，用户不感知
        return "OK: 排队中，请稍后查看订单";
    }

    // ================================================================
    //  第 1 关：令牌桶限流
    // ================================================================

    /**
     * 令牌桶算法：尝试获取一个令牌
     *
     * 原理：
     *   - 桶里最多放 500 个令牌（容量）
     *   - 每秒补充 200 个令牌（速率）
     *   - 每个请求消耗 1 个令牌
     *   - 桶空了 → 拒绝请求
     *
     * 和固定窗口的区别：
     *   固定窗口：0-1秒允许100个，1-2秒允许100个
     *            如果 0.9秒 来了100个，1.1秒 来了100个 → 0.2秒内200个请求！
     *   令牌桶：  桶里始终有上限，不管什么时间边界，只认令牌数
     */
    private static boolean tryAcquireToken() {
        // 先补充令牌
        refillTokens();
        // CAS 扣减：令牌 > 0 才能扣
        while (true) {
            int current = tokenBucket.get();
            if (current <= 0) return false;
            if (tokenBucket.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    /** 补充令牌（每秒 200 个） */
    private static void refillTokens() {
        long now = System.currentTimeMillis();
        long last = lastTokenRefillTime.get();
        long elapsed = now - last;

        if (elapsed >= 1000) {  // 每 1 秒补充一次
            if (lastTokenRefillTime.compareAndSet(last, now)) {
                int newTokens = Math.min(
                        tokenBucket.get() + TOKEN_REFILL_RATE,
                        TOKEN_BUCKET_CAPACITY
                );
                tokenBucket.set(newTokens);
            }
        }
    }

    /** 定时补充令牌的后台线程 */
    private static void startTokenRefiller() {
        tokenRefiller.scheduleAtFixedRate(() -> {
            int newTokens = Math.min(
                    tokenBucket.get() + TOKEN_REFILL_RATE,
                    TOKEN_BUCKET_CAPACITY
            );
            tokenBucket.set(newTokens);
        }, 1, 1, TimeUnit.SECONDS);
    }

    // ================================================================
    //  第 2 关：缓存查询（穿透 + 击穿 + 雪崩 三层保护）
    // ================================================================

    /**
     * 从缓存获取演出信息
     *
     * 面试考点：缓存三兄弟——穿透、击穿、雪崩
     *
     * 穿透：查不存在的数据 → 缓存 NULL 占位（60s TTL）
     * 击穿：热点 key 过期，大量请求打 DB → SET NX 互斥锁，只让一个线程重建
     * 雪崩：大量 key 同时过期 → TTL 加随机值（±20%）
     */
    private static ShowInfo getShowFromCache(long showId) {
        // 1. 先查 NULL 缓存（防穿透：如果之前查过不存在，直接返回 null）
        if (nullCache.contains(showId)) {
            return null;
        }

        // 2. 查缓存
        ShowInfo show = showCache.get(showId);
        if (show != null) {
            return show;  // 缓存命中，直接返回
        }

        // 3. 缓存未命中 → 尝试重建（防击穿：用互斥锁）
        //    SET NX：只有第一个线程能拿到锁
        boolean gotLock = cacheRebuildLock.putIfAbsent(showId, true) == null;
        if (gotLock) {
            try {
                // Double Check：拿到锁后再查一次，可能其他线程已经重建好了
                show = showCache.get(showId);
                if (show != null) {
                    return show;
                }

                // 模拟查数据库（耗时操作）
                show = queryShowFromDB(showId);

                if (show != null) {
                    // 数据存在：写入缓存，TTL 随机 300-360 秒（防雪崩）
                    showCache.put(showId, show);
                } else {
                    // 数据不存在：缓存 NULL 占位（防穿透）
                    nullCache.add(showId);
                }
            } finally {
                cacheRebuildLock.remove(showId);  // 释放锁
            }
        } else {
            // 没拿到锁，等 50ms 让持锁线程重建完，再查一次
            try { Thread.sleep(50); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); }
            show = showCache.get(showId);
        }

        return show;
    }

    /** 模拟查数据库（50ms 延迟） */
    private static ShowInfo queryShowFromDB(long showId) {
        try { Thread.sleep(50); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); }
        // 只有 showId=1 的演出存在
        if (showId == 1) {
            return new ShowInfo(showId, "周杰伦演唱会", TOTAL_STOCK);
        }
        return null;
    }

    /** 初始化缓存：把演出信息预热到 Redis */
    private static void initShowCache() {
        showCache.put(1L, new ShowInfo(1L, "周杰伦演唱会", TOTAL_STOCK));
        System.out.println("✅ 缓存预热完成：周杰伦演唱会已加载到 Redis\n");
    }

    // ================================================================
    //  第 3 关：Redis 原子预扣库存
    // ================================================================

    /**
     * 模拟 Redis Lua 脚本原子扣库存
     *
     * 真实 Lua 脚本（stock_deduct.lua）：
     *   local stock = redis.call('GET', KEYS[1])
     *   if stock and tonumber(stock) >= tonumber(ARGV[1]) then
     *       redis.call('DECRBY', KEYS[1], ARGV[1])
     *       return 1
     *   end
     *   return 0
     *
     * 这里用 AtomicInteger.compareAndSet 模拟 Redis 单线程的原子性
     */
    private static boolean redisAtomicDeduct(int quantity) {
        while (true) {
            int current = redisStock.get();
            if (current < quantity) {
                return false;  // 库存不足
            }
            // CAS 模拟 Redis 原子 DECR + 判断
            if (redisStock.compareAndSet(current, current - quantity)) {
                return true;
            }
            // CAS 失败 → 其他线程修改了库存 → 重试
        }
    }

    // ================================================================
    //  第 4 关：用户防重锁
    // ================================================================

    /**
     * 获取用户防重锁（10 秒）
     *
     * 真实 Redis 命令：
     *   SET livetix:user:order:lock:{userId}:{showId} 1 EX 10 NX
     *
     * 为什么是 10 秒？
     *   - 防重复提交：用户手抖点了两次，10 秒内第二次被拦截
     *   - 允许重试：10 秒后锁过期，用户可以重新下单（体验好）
     *   - 不永久：如果永久锁，MQ 消费失败库存回滚了，用户也无法重试
     */
    private static boolean acquireUserLock(long userId, long showId) {
        long now = System.currentTimeMillis();
        Long existingLock = userOrderLock.putIfAbsent(userId, now);
        if (existingLock == null) {
            return true;  // 锁不存在，获取成功
        }
        // 锁已存在，检查是否过期（10 秒）
        if (now - existingLock > 10_000) {
            // 锁过期了，用 CAS 替换（防止多个线程同时替换）
            return userOrderLock.replace(userId, existingLock, now);
        }
        return false;  // 锁未过期，拒绝
    }

    private static void releaseUserLock(long userId, long showId) {
        userOrderLock.remove(userId);
    }

    // ================================================================
    //  第 5 关：MQ 消费者（削峰填谷）
    // ================================================================

    /**
     * 启动 MQ 消费者线程池
     *
     * 真实场景：RocketMQ 消费者，@RocketMQMessageListener 注解
     * 这里用 BlockingQueue + 10 个线程模拟
     *
     * 削峰填谷原理：
     *   10000 个请求瞬间涌来 → 全部进 MQ 队列
     *   10 个消费者慢慢处理 → 每秒处理约 50 个（每个 200ms）
     *   用户侧：请求瞬间返回（< 5ms），不需要等 DB 写入
     */
    private static void startMqConsumers() {
        for (int i = 0; i < 10; i++) {
            final int consumerId = i;
            consumerPool.execute(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        OrderMessage msg = orderQueue.poll(1, TimeUnit.SECONDS);
                        if (msg == null) continue;

                        // 模拟 DB 写入（耗时操作）
                        processOrderInDB(msg);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
    }

    // ================================================================
    //  第 6 关：DB 乐观锁写入（最终防线）
    // ================================================================

    /**
     * 模拟 DB 层的订单写入（带乐观锁）
     *
     * 真实 SQL（ShowMapper.deductStock）：
     *   UPDATE t_show SET available_stock = available_stock - #{quantity}
     *   WHERE id = #{showId} AND available_stock >= #{quantity}
     *
     * 核心逻辑：
     *   1. CAS 尝试扣库存（模拟 SQL 的 WHERE 条件）
     *   2. 成功 → 创建订单，记录成功
     *   3. 失败 → 库存不足，回滚 Redis 预扣 + 释放锁
     *
     * 为什么这是最后一道防线？
     *   - Redis 可能挂了，库存数据不一致
     *   - MQ 消费可能重复（Redis 扣了但 DB 没扣）
     *   - 只有 DB 的 WHERE 条件能保证最终一致性
     */
    private static void processOrderInDB(OrderMessage msg) {
        // 模拟 DB 写入耗时（200ms，包含网络+磁盘IO）
        try { Thread.sleep(200); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return; }

        // CAS 模拟 SQL 乐观锁：WHERE available_stock >= quantity
        boolean success = false;
        while (!success) {
            int current = dbStock.get();
            if (current < msg.quantity) {
                // 库存不足！回滚 Redis 预扣 + 释放锁
                redisStock.addAndGet(msg.quantity);
                releaseUserLock(msg.userId, msg.showId);
                dbWriteFail.incrementAndGet();
                return;
            }
            success = dbStock.compareAndSet(current, current - msg.quantity);
        }

        // 扣减成功 → 订单创建
        dbWriteSuccess.incrementAndGet();
        orderSuccess.incrementAndGet();
        // 下单成功后释放用户锁（允许用户再次购买其他场次）
        releaseUserLock(msg.userId, msg.showId);
    }

    // ================================================================
    //  最终统计报告
    // ================================================================

    private static void printFinalReport(long totalTime) {
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                     📊 秒杀结果统计报告                        ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        System.out.println(String.format("║  总请求数:         %-8d                                  ║", USER_COUNT));
        System.out.println(String.format("║  总耗时:           %-8d ms                               ║", totalTime));
        System.out.println(String.format("║  QPS:              %-8.0f 请求/秒                          ║",
                USER_COUNT * 1000.0 / totalTime));
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  [第1关] 令牌桶限流:                                         ║");
        System.out.println(String.format("║    被拒绝:         %-8d                                   ║",
                rateLimitRejectCount.get()));
        System.out.println("║                                                              ║");
        System.out.println("║  [第3关] Redis 预扣库存:                                     ║");
        System.out.println(String.format("║    最终剩余:       %-8d                                   ║",
                redisStock.get()));
        System.out.println("║                                                              ║");
        System.out.println("║  [第5关] MQ 消息队列:                                        ║");
        System.out.println(String.format("║    发送成功:       %-8d                                   ║",
                mqSendSuccess.get()));
        System.out.println(String.format("║    发送失败:       %-8d                                   ║",
                mqSendFail.get()));
        System.out.println("║                                                              ║");
        System.out.println("║  [第6关] DB 乐观锁写入:                                      ║");
        System.out.println(String.format("║    DB库存剩余:     %-8d                                   ║",
                dbStock.get()));
        System.out.println(String.format("║    写入成功:       %-8d                                   ║",
                dbWriteSuccess.get()));
        System.out.println(String.format("║    写入失败:       %-8d                                   ║",
                dbWriteFail.get()));
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println(String.format("║  🎯 最终下单成功:  %-8d (应等于总库存 %d)                   ║",
                orderSuccess.get(), TOTAL_STOCK));
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        // 验证结果
        if (orderSuccess.get() == TOTAL_STOCK && dbStock.get() == 0) {
            System.out.println("\n✅ 验证通过！无超卖，无少卖，库存精准扣减！");
        } else if (orderSuccess.get() > TOTAL_STOCK) {
            System.out.println("\n❌ 超卖！下单数(" + orderSuccess.get()
                    + ") > 库存(" + TOTAL_STOCK + ")");
        } else {
            System.out.println("\n⚠️ 少卖！下单数(" + orderSuccess.get()
                    + ") < 库存(" + TOTAL_STOCK + ")，可能有限流导致");
        }
    }

    // ================================================================
    //  内部类
    // ================================================================

    /** 演出信息（对应 t_show 表） */
    static class ShowInfo {
        long id;
        String name;
        int stock;

        ShowInfo(long id, String name, int stock) {
            this.id = id;
            this.name = name;
            this.stock = stock;
        }
    }

    /** MQ 订单消息（对应 RocketMQ Message） */
    static class OrderMessage {
        long userId;
        long showId;
        int quantity;
        long timestamp;

        OrderMessage(long userId, long showId, int quantity, long timestamp) {
            this.userId = userId;
            this.showId = showId;
            this.quantity = quantity;
            this.timestamp = timestamp;
        }
    }
}