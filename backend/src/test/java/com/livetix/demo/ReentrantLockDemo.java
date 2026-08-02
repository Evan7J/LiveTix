package com.livetix.demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Demo 5-3: ReentrantLock 修复超卖问题
 *
 * 对比 synchronized，ReentrantLock 提供了：
 * 1. lock() / unlock() — 基础加锁解锁
 * 2. tryLock() — 尝试获取锁，拿不到立即返回 false（不阻塞！）
 * 3. tryLock(time, unit) — 带超时等待
 * 4. 公平锁 — 先到先得，避免线程饥饿
 * 5. Condition — 更精细的线程通信
 *
 * 面试点：synchronized 是 JVM 层面的关键字，ReentrantLock 是 JDK 的 API 类。
 *         ReentrantLock 基于 AQS（AbstractQueuedSynchronizer）实现。
 */
public class ReentrantLockDemo {

    // ========== 共享资源 ==========
    private static int stock = 100;                           // 库存
    private static final AtomicInteger soldCount = new AtomicInteger(0); // 卖出数量

    // ========== 锁对象 ==========
    private static final ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== Demo 5-3: ReentrantLock 修复超卖 ==========\n");

        // ===== 场景 1: lock() / unlock() 基础用法 =====
        testBasicLock();

        // 重置库存
        stock = 100;
        soldCount.set(0);

        // ===== 场景 2: tryLock() 尝试获取锁 =====
        testTryLock();

        System.out.println("\n========== Demo 5-3 完成 ==========");
    }

    /**
     * 场景 1: lock() / unlock() — 与 synchronized 等价，但需要手动释放
     *
     * 核心规则（笔试常考）：
     *   lock() 必须写在 try 外面！
     *   unlock() 必须写在 finally 里面！
     *
     * 错误写法：
     *   try {
     *       lock.lock();   // ❌ 如果 lock() 抛异常，会执行 finally 的 unlock()
     *       // 业务代码     //    但锁根本就没拿到，unlock() 会抛 IllegalMonitorStateException
     *   } finally {
     *       lock.unlock();
     *   }
     *
     * 正确写法：
     *   lock.lock();
     *   try {
     *       // 业务代码
     *   } finally {
     *       lock.unlock();
     *   }
     */
    private static void testBasicLock() throws InterruptedException {
        System.out.println("【场景 1】lock() / unlock() 基础加锁——200线程并发扣减100库存\n");

        int threadCount = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    // 1. 加锁（写在 try 外面！）
                    lock.lock();
                    try {
                        // 2. 临界区：检查库存 + 扣减
                        if (stock > 0) {
                            stock--;                          // 原子操作（因为持有锁）
                            soldCount.incrementAndGet();
                        }
                    } finally {
                        // 3. 释放锁（写在 finally 里！）
                        lock.unlock();
                    }
                } finally {
                    latch.countDown();
                }
            }, "买家-" + i).start();
        }

        latch.await();
        System.out.println("  最终库存:  " + stock);
        System.out.println("  卖出数量:  " + soldCount.get());
        System.out.println("  结果验证:  " + (stock == 0 && soldCount.get() == 100 ? "✅ 无超卖" : "❌ 异常"));
        System.out.println("  解释: 同一时刻只有一个线程能执行 lock() 内的代码，保证 stock-- 不会交错执行\n");
    }

    /**
     * 场景 2: tryLock() — 尝试获取锁，拿不到不等，直接走人
     *
     * 实际业务场景（面试高频）：
     *   - 秒杀时用户已抢到，防止重复下单：tryLock 拿不到说明已有订单在处理
     *   - 分布式锁的本地降级：拿不到 Redis 锁就返回"系统繁忙"
     *   - 定时任务的幂等：同一任务只允许一个实例执行
     *
     * tryLock() 有三个重载版本：
     *   tryLock()              — 立即返回，拿不到就不等了
     *   tryLock(time, unit)    — 等待指定时间，超时返回 false
     *   tryLock() 不加参数      — 等价于不等待
     */
    private static void testTryLock() throws InterruptedException {
        System.out.println("【场景 2】tryLock() 尝试获取锁——模拟抢票场景\n");

        // 模拟 10 个线程抢票，但只有一个能抢到
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger tryLockSuccess = new AtomicInteger(0); // tryLock 成功次数
        AtomicInteger tryLockFail = new AtomicInteger(0);    // tryLock 失败次数

        for (int i = 0; i < threadCount; i++) {
            final int buyerId = i;
            new Thread(() -> {
                try {
                    // tryLock(): 尝试获取锁
                    //   返回 true  → 获取成功，执行业务
                    //   返回 false → 获取失败，不等了，直接走人
                    if (lock.tryLock()) {
                        try {
                            tryLockSuccess.incrementAndGet();
                            System.out.println("    买家" + buyerId + " ✅ 抢到锁，处理中...");
                            Thread.sleep(100); // 模拟业务处理
                        } finally {
                            lock.unlock();
                        }
                    } else {
                        tryLockFail.incrementAndGet();
                        System.out.println("    买家" + buyerId + " ❌ 锁被占用，直接返回");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        System.out.println("\n  tryLock 成功: " + tryLockSuccess.get() + " 次");
        System.out.println("  tryLock 失败: " + tryLockFail.get() + " 次");
        System.out.println("  解释: 10个线程同时抢锁，只有1个能拿到，其余9个 tryLock 返回 false 直接放弃\n");
    }
}