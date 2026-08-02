package com.livetix.demo;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo 5-5: 线程池——7 参数 + 4 拒绝策略
 *
 * 为什么不用 new Thread().start()？
 *   1. 创建/销毁线程开销大（JVM 要分配栈内存、与 OS 线程映射）
 *   2. 线程数不可控，1000 个请求 = 1000 个线程 → OOM
 *   3. 缺乏管理能力（无法监控、无法统一关闭）
 *
 * 线程池核心：ThreadPoolExecutor 的 7 个参数
 *
 * ThreadPoolExecutor(
 *     int corePoolSize,        // 参数1: 核心线程数（常驻线程，即使空闲也不销毁）
 *     int maximumPoolSize,     // 参数2: 最大线程数（核心 + 临时）
 *     long keepAliveTime,      // 参数3: 临时线程空闲存活时间
 *     TimeUnit unit,           // 参数4: 时间单位
 *     BlockingQueue<Runnable> workQueue,  // 参数5: 任务队列（阻塞队列）
 *     ThreadFactory threadFactory,        // 参数6: 线程工厂（给线程起名字）
 *     RejectedExecutionHandler handler     // 参数7: 拒绝策略
 * )
 *
 * 面试点：线程池任务执行流程（必考！）
 *   1. 任务来了 → 先看核心线程满了没？
 *   2. 没满 → 创建核心线程执行
 *   3. 满了 → 扔进任务队列排队
 *   4. 队列满了 → 创建临时线程（最多到 maximumPoolSize）
 *   5. 临时线程也满了 → 执行拒绝策略
 */
public class ThreadPoolDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== Demo 5-5: 线程池七大参数 + 四大拒绝策略 ==========\n");

        testSevenParams();
        testRejectionPolicies();
        testBuiltInPools();

        System.out.println("\n========== Demo 5-5 完成 ==========");
    }

    /**
     * 场景 1: 七大参数详解——模拟任务提交流程
     *
     * 配置含义：
     *   核心线程 = 2（常驻，一直活着）
     *   最大线程 = 4（最多再创建 2 个临时线程）
     *   空闲超时 = 3 秒（临时线程 3 秒没活干就销毁）
     *   队列容量 = 3（最多排队 3 个任务）
     *
     * 任务流程：
     *   第1-2个任务 → 核心线程执行
     *   第3-5个任务 → 进队列排队
     *   第6-7个任务 → 创建临时线程执行
     *   第8个及以后 → 拒绝！
     */
    private static void testSevenParams() {
        System.out.println("【场景 1】七大参数详解——任务提交流程\n");
        System.out.println("配置: 核心2 | 最大4 | 队列3 | 超时3秒\n");

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,                              // 核心线程数
                4,                              // 最大线程数
                3, TimeUnit.SECONDS,            // 临时线程空闲3秒后销毁
                new LinkedBlockingQueue<>(3),   // 有界队列，容量3
                new ThreadFactory() {           // 自定义线程工厂：给线程起名字
                    private final AtomicInteger count = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "票务线程-" + count.getAndIncrement());
                    }
                },
                new ThreadPoolExecutor.AbortPolicy() // 拒绝策略：抛异常
        );

        // 提交 8 个任务（超过 4+3=7 的处理能力）
        for (int i = 1; i <= 8; i++) {
            final int taskId = i;
            try {
                executor.execute(() -> {
                    System.out.println("  [" + Thread.currentThread().getName()
                            + "] 正在处理任务" + taskId);
                    try {
                        Thread.sleep(2000); // 模拟任务处理耗时
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println("  [" + Thread.currentThread().getName()
                            + "] 任务" + taskId + " 完成");
                });
                System.out.println("  任务" + taskId + " 提交成功");
            } catch (RejectedExecutionException e) {
                System.out.println("  任务" + taskId + " ❌ 被拒绝！原因: " + e.getMessage());
            }
        }

        // 等待一会，观察临时线程是否被销毁
        try { Thread.sleep(8000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        System.out.println("\n  8秒后当前线程数: " + executor.getPoolSize()
                + "（核心线程保留，临时线程已销毁）\n");

        executor.shutdown();
    }

    /**
     * 场景 2: 四大拒绝策略
     *
     * 当线程池和队列都满了，新任务怎么处理？
     *
     * 策略                      | 行为
     * AbortPolicy（默认）        | 抛 RejectedExecutionException，不执行
     * CallerRunsPolicy           | 让提交任务的线程自己执行（谁提交谁执行）
     * DiscardPolicy              | 直接丢弃，不抛异常（静默丢弃，危险！）
     * DiscardOldestPolicy        | 丢弃队列里最老的任务，把新任务塞进去
     */
    private static void testRejectionPolicies() throws InterruptedException {
        System.out.println("【场景 2】四大拒绝策略对比\n");

        // 用一个小线程池做演示：核心1 | 最大1 | 队列1 → 最多处理2个任务
        // 提交3个任务，第3个触发拒绝策略

        testRejection("AbortPolicy（抛异常）",
                new ThreadPoolExecutor.AbortPolicy());

        testRejection("CallerRunsPolicy（调用者执行）",
                new ThreadPoolExecutor.CallerRunsPolicy());

        testRejection("DiscardPolicy（静默丢弃）",
                new ThreadPoolExecutor.DiscardPolicy());

        testRejection("DiscardOldestPolicy（丢弃最老任务）",
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    private static void testRejection(String name, RejectedExecutionHandler handler)
            throws InterruptedException {
        System.out.println("  " + name + ":");
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1),
                handler
        );

        for (int i = 1; i <= 3; i++) {
            final int taskId = i;
            try {
                executor.execute(() -> {
                    System.out.println("    任务" + taskId + " 由 "
                            + Thread.currentThread().getName() + " 执行");
                    try { Thread.sleep(500); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); }
                });
            } catch (RejectedExecutionException e) {
                System.out.println("    任务" + taskId + " ❌ 拒绝: " + e.getMessage());
            }
        }

        Thread.sleep(1500); // 等任务执行完
        executor.shutdown();
        System.out.println();
    }

    /**
     * 场景 3: Executors 内置线程池（面试高频陷阱）
     *
     * Executors 提供了快捷创建线程池的方法，但大部分都有坑：
     *
     * newFixedThreadPool(n)      → 固定大小线程池
     *    坑：队列是 LinkedBlockingQueue（无界！），任务堆积会 OOM
     *
     * newCachedThreadPool()      → 弹性线程池（来一个任务建一个线程）
     *    坑：最大线程数是 Integer.MAX_VALUE，无限创建线程 → OOM
     *
     * newSingleThreadExecutor()  → 单线程线程池
     *    坑：同 FixedThreadPool，队列无界
     *
     * newScheduledThreadPool(n)  → 定时任务线程池
     *    唯一比较安全的，但最大线程数也是 Integer.MAX_VALUE
     *
     * 阿里规约：强制使用 ThreadPoolExecutor 手动创建，不允许用 Executors！
     */
    private static void testBuiltInPools() {
        System.out.println("【场景 3】Executors 内置线程池 vs 手动创建\n");

        // 推荐写法：手动指定所有参数
        ThreadPoolExecutor recommended = new ThreadPoolExecutor(
                5, 10, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        System.out.println("  ✅ 推荐: new ThreadPoolExecutor(...) 手动指定所有参数");
        System.out.println("     核心5 | 最大10 | 队列100 | 超时60秒 | CallerRunsPolicy\n");

        // 不推荐写法（阿里规约禁止）
        System.out.println("  ❌ 不推荐: Executors.newFixedThreadPool(10)");
        System.out.println("     原因: 队列是 LinkedBlockingQueue（无界），任务堆积会 OOM");
        System.out.println("  ❌ 不推荐: Executors.newCachedThreadPool()");
        System.out.println("     原因: 最大线程数 = Integer.MAX_VALUE，无限创建线程会 OOM\n");

        // 线程池大小计算公式（经验值）
        System.out.println("  线程池大小经验公式:");
        System.out.println("    CPU密集型: CPU核数 + 1");
        System.out.println("    IO密集型:  CPU核数 * 2（或 CPU核数 / (1 - 阻塞系数)）");
        System.out.println("    当前机器 CPU 核数: " + Runtime.getRuntime().availableProcessors());

        recommended.shutdown();
    }
}