package com.livetix.demo;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo 8-2: 三层缓存保护——穿透、击穿、雪崩，逐个击破
 *
 * ============================================================
 * 面试场景还原：
 * ============================================================
 *
 * 面试官："你们的系统用了缓存，那缓存穿透、击穿、雪崩分别是什么？怎么解决的？"
 *
 * 标准回答：
 *   "穿透是查不存在的数据，攻击者用随机ID打穿缓存直接打到DB。
 *    我们用 NULL 占位符，不存在的 key 也缓存 60 秒。
 *
 *    击穿是热点 key 过期瞬间，大量请求同时打到 DB。
 *    我们用 SET NX 互斥锁，只让一个线程去查 DB 重建缓存，其他线程等待。
 *
 *    雪崩是大量 key 同时过期，DB 瞬间压力过大。
 *    我们给每个 key 的 TTL 加 ±20% 随机值，避免同时过期。"
 *
 * ============================================================
 * 这个 Demo 模拟了什么？
 * ============================================================
 *
 * 用一个模拟的"数据库"（Map）和"缓存"（Map），通过多线程并发请求，
 * 对比有保护和无保护时数据库被查询的次数，直观展示三层保护的效果。
 *
 * 每个场景的输出：
 *   - DB 查询次数：反映数据库压力
 *   - 缓存命中次数：反映缓存效果
 *   - 总耗时：反映响应速度
 */
public class CacheProtectionDemo {

    // ========== 模拟数据库（每次查询耗时 200ms，模拟磁盘IO） ==========
    private static final Map<Long, String> database = new ConcurrentHashMap<>();
    private static final AtomicInteger dbQueryCount = new AtomicInteger(0);

    static {
        // 数据库里只有 3 条数据（模拟真实业务：只有少量演出）
        database.put(1L, "周杰伦演唱会");
        database.put(2L, "五月天演唱会");
        database.put(3L, "陈奕迅演唱会");
    }

    /** 模拟数据库查询（耗时操作） */
    private static String queryDB(long id) {
        dbQueryCount.incrementAndGet();
        try { Thread.sleep(200); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); }
        return database.get(id);  // 不存在返回 null
    }

    // ========== 工具方法 ==========
    private static void resetCounters() {
        dbQueryCount.set(0);
    }

    private static void printSeparator(String title) {
        System.out.println("\n" + "═".repeat(60));
        System.out.println("  " + title);
        System.out.println("═".repeat(60));
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║     Demo 8-2: 三层缓存保护——穿透、击穿、雪崩                  ║");
        System.out.println("║     每个场景独立对比：无保护 vs 有保护                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        testPenetration();   // 场景 1: 缓存穿透
        testBreakdown();     // 场景 2: 缓存击穿
        testAvalanche();     // 场景 3: 缓存雪崩

        System.out.println("\n========== Demo 8-2 完成 ==========");
    }

    // ================================================================
    //  场景 1: 缓存穿透 —— 查不存在的数据
    // ================================================================

    /**
     * 问题场景：
     *   攻击者用随机 ID（如 999、888、777）疯狂请求，
     *   缓存里没有，数据库里也没有，每次请求都穿透缓存打到 DB。
     *
     * 解决方案：
     *   不存在的 key 也缓存，value 存一个空值占位符（如 "__NULL__"），
     *   下次同样的请求直接从缓存返回空值，不再打 DB。
     *   TTL 设短一点（60秒），因为万一数据后续被创建了，能较快感知到。
     *
     * 面试追问：为什么 NULL 也要缓存？
     *   因为不缓存的话，攻击者换个 ID 继续打，每次都能穿透。
     *   缓存了 NULL，攻击者无论换多少 ID，每个 ID 只穿透一次。
     */
    private static void testPenetration() throws InterruptedException {
        printSeparator("场景 1: 缓存穿透（大量请求查不存在的数据）");

        // ===== 无保护：1000 个请求查不存在的 ID =====
        System.out.println("\n  【无保护】1000 个请求查 10 个不存在的 ID...");
        resetCounters();
        Map<Long, String> cacheNoProtect = new ConcurrentHashMap<>();

        long start = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch latch1 = new CountDownLatch(1000);

        for (int i = 0; i < 1000; i++) {
            final long id = 100 + (i % 10);  // 10 个不存在的 ID 循环
            pool.execute(() -> {
                try {
                    String val = cacheNoProtect.get(id);
                    if (val == null) {
                        val = queryDB(id);  // 缓存没有 → 查DB（每次都穿透！）
                        if (val != null) cacheNoProtect.put(id, val);
                        // 注意：val 是 null 时没有缓存，下次还是穿透！
                    }
                } finally { latch1.countDown(); }
            });
        }
        latch1.await();
        long timeNoProtect = System.currentTimeMillis() - start;

        System.out.println("    DB 查询次数: " + dbQueryCount.get() + " 次");
        System.out.println("    总耗时:      " + timeNoProtect + " ms");
        System.out.println("    问题: 每个请求都穿透缓存打 DB，1000 次请求 = 1000 次 DB 查询");

        // ===== 有保护：NULL 占位符 =====
        System.out.println("\n  【有保护】同样的 1000 个请求，加 NULL 缓存...");
        resetCounters();
        Map<Long, String> cacheWithProtect = new ConcurrentHashMap<>();
        final String NULL_PLACEHOLDER = "__NULL__";  // 空值占位

        start = System.currentTimeMillis();
        CountDownLatch latch2 = new CountDownLatch(1000);

        for (int i = 0; i < 1000; i++) {
            final long id = 100 + (i % 10);
            pool.execute(() -> {
                try {
                    String val = cacheWithProtect.get(id);
                    if (val == null) {
                        // 缓存没有 → 查 DB
                        val = queryDB(id);
                        // 关键：不管查没查到，都缓存！
                        cacheWithProtect.put(id, val != null ? val : NULL_PLACEHOLDER);
                    }
                    // 如果 val == NULL_PLACEHOLDER，说明之前查过，不存在，直接返回 null
                } finally { latch2.countDown(); }
            });
        }
        latch2.await();
        long timeWithProtect = System.currentTimeMillis() - start;

        System.out.println("    DB 查询次数: " + dbQueryCount.get() + " 次");
        System.out.println("    总耗时:      " + timeWithProtect + " ms");
        System.out.println("    效果: 只查了 10 次 DB（每种 ID 只查一次），其余 990 次走缓存");

        pool.shutdown();
        System.out.println("\n  📊 对比: " + dbQueryCount.get() + " 次 vs 1000 次，"
                + "DB 压力降低 " + (100 - dbQueryCount.get() * 100 / 1000) + "%");
    }

    // ================================================================
    //  场景 2: 缓存击穿 —— 热点 key 过期瞬间
    // ================================================================

    /**
     * 问题场景：
     *   一个热门演出（如周杰伦演唱会）缓存刚好过期，
     *   瞬间 1000 个请求同时打到 DB，DB 可能直接挂掉。
     *
     * 解决方案：
     *   SET NX 互斥锁：只有第一个线程能拿到锁去查 DB 重建缓存，
     *   其他线程等待（或返回旧值），避免大量请求同时打 DB。
     *
     * 面试追问：为什么叫 SET NX？
     *   SET key value NX：如果 key 不存在就设置，存在就返回失败。
     *   这是 Redis 的原子命令，保证只有一个线程能设置成功。
     *
     * 面试追问：SET NX 会不会死锁？
     *   会！如果拿到锁的线程挂了，锁永远不释放。
     *   解决方案：SET key value EX 10 NX（加过期时间，10 秒自动释放）
     */
    private static void testBreakdown() throws InterruptedException {
        printSeparator("场景 2: 缓存击穿（热点 key 过期瞬间，大量请求打 DB）");

        // ===== 无保护：热点 key 过期，1000 个请求同时打 DB =====
        System.out.println("\n  【无保护】热点 key 过期，1000 个并发请求查同一个 ID...");
        resetCounters();
        Map<Long, String> cacheNoProtect = new ConcurrentHashMap<>();
        // 缓存是空的，模拟热点 key 刚过期

        long start = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(100);
        CountDownLatch latch1 = new CountDownLatch(1000);

        for (int i = 0; i < 1000; i++) {
            pool.execute(() -> {
                try {
                    String val = cacheNoProtect.get(1L);
                    if (val == null) {
                        val = queryDB(1L);  // 1000 个线程同时查 DB！
                        if (val != null) cacheNoProtect.put(1L, val);
                    }
                } finally { latch1.countDown(); }
            });
        }
        latch1.await();
        long timeNoProtect = System.currentTimeMillis() - start;

        System.out.println("    DB 查询次数: " + dbQueryCount.get() + " 次");
        System.out.println("    总耗时:      " + timeNoProtect + " ms");
        System.out.println("    问题: 1000 个线程同时查 DB，DB 直接被打爆！");

        // ===== 有保护：SET NX 互斥锁 + Double Check =====
        System.out.println("\n  【有保护】SET NX 互斥锁，只让一个线程查 DB...");
        resetCounters();
        Map<Long, String> cacheWithProtect = new ConcurrentHashMap<>();
        // 互斥锁：key → true（拿了锁）
        Map<Long, Boolean> rebuildLock = new ConcurrentHashMap<>();

        start = System.currentTimeMillis();
        CountDownLatch latch2 = new CountDownLatch(1000);

        for (int i = 0; i < 1000; i++) {
            pool.execute(() -> {
                try {
                    String val = cacheWithProtect.get(1L);
                    if (val == null) {
                        // 尝试获取互斥锁（SET NX）
                        boolean gotLock = rebuildLock.putIfAbsent(1L, true) == null;
                        if (gotLock) {
                            try {
                                // Double Check：拿到锁后再查一次缓存
                                val = cacheWithProtect.get(1L);
                                if (val == null) {
                                    val = queryDB(1L);  // 只有这一个线程查 DB！
                                    if (val != null) cacheWithProtect.put(1L, val);
                                }
                            } finally {
                                rebuildLock.remove(1L);  // 释放锁
                            }
                        } else {
                            // 没拿到锁，等 50ms 让持锁线程重建完
                            try { Thread.sleep(50); } catch (InterruptedException e) {
                                Thread.currentThread().interrupt(); }
                            val = cacheWithProtect.get(1L);
                        }
                    }
                } finally { latch2.countDown(); }
            });
        }
        latch2.await();
        long timeWithProtect = System.currentTimeMillis() - start;

        System.out.println("    DB 查询次数: " + dbQueryCount.get() + " 次");
        System.out.println("    总耗时:      " + timeWithProtect + " ms");
        System.out.println("    效果: 只查了 1 次 DB，其余 999 个线程等缓存重建后直接读缓存");

        pool.shutdown();
        System.out.println("\n  📊 对比: 1 次 vs 1000 次，DB 压力降低 99.9%");
    }

    // ================================================================
    //  场景 3: 缓存雪崩 —— 大量 key 同时过期
    // ================================================================

    /**
     * 问题场景：
     *   假设所有演出信息缓存 TTL 都是 3600 秒（1 小时），
     *   在某个整点全部同时过期，瞬间大量请求打到 DB。
     *
     * 解决方案：
     *   TTL 加随机值（如 300-360 秒），让 key 在不同时间过期，
     *   避免同时失效导致的 DB 压力。
     *
     * 面试追问：为什么是 ±20% 而不是更大的范围？
     *   20% 足够分散过期时间，同时不会让数据过期太久。
     *   如果 TTL 是 300 秒，加上 20% 随机 = 300-360 秒，
     *   最大差距 60 秒，足够让 DB 压力分散开来。
     */
    private static void testAvalanche() throws InterruptedException {
        printSeparator("场景 3: 缓存雪崩（大量 key 同时过期）");

        System.out.println();
        System.out.println("  缓存雪崩不像穿透和击穿那样容易用代码『模拟』，");
        System.out.println("  因为它的核心是 TTL 设置策略。这里展示 TTL 计算逻辑。\n");

        int baseTTL = 300;  // 基础 TTL：300 秒（5 分钟）

        System.out.println("  【无保护】所有 key 相同的 TTL = " + baseTTL + " 秒");
        System.out.println("    问题: 假设 100 个演出信息缓存都在 10:00 加载，");
        System.out.println("          到 10:05 全部同时过期，100 个请求同时打 DB。\n");

        System.out.println("  【有保护】每个 key 随机 TTL = " + baseTTL + " + random(0, 60) 秒");
        System.out.println("    效果: 100 个 key 的过期时间分散在 10:05:00 ~ 10:06:00，");
        System.out.println("          DB 压力被平均分散到 60 秒内，不会瞬间打爆。\n");

        // 演示 TTL 计算
        System.out.println("  TTL 计算示例（LiveTix 实际代码逻辑）：");
        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            int ttl = baseTTL + random.nextInt(61);  // 300-360 秒
            System.out.println("  │  key #" + (i + 1) + "  TTL = " + ttl
                    + " 秒 (" + (ttl - baseTTL) + " 秒随机偏移)              │");
        }
        System.out.println("  └─────────────────────────────────────────────────────┘");

        System.out.println("\n  📌 LiveTix 项目中的实际代码位置：");
        System.out.println("     ShowService.java → jitteredTtl() 方法");
        System.out.println("     每次写入缓存时调用：redisTemplate.opsForValue().set(");
        System.out.println("         key, value, jitteredTtl(300), TimeUnit.SECONDS);");
        System.out.println();
        System.out.println("     jitteredTtl 实现：");
        System.out.println("         return baseTtl + ThreadLocalRandom.current().nextInt(baseTtl / 5);");
        System.out.println("         // baseTtl / 5 = 300 / 5 = 60，即 ±20% = 0~60 秒随机");
    }
}