package com.livetix.demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo 5-4: AtomicInteger 无锁方案——基于 CAS 实现线程安全
 *
 * 核心概念：CAS（Compare And Swap，比较并交换）
 *
 * CAS 是一道 CPU 原子指令，伪代码：
 *   boolean compareAndSet(期望值, 新值) {
 *       if (当前值 == 期望值) {
 *           当前值 = 新值;
 *           return true;
 *       }
 *       return false;
 *   }
 *   整个过程是 CPU 级别的原子操作，不会被中断！
 *
 * 面试点：CAS 不加锁，为什么能保证线程安全？
 *        答：因为 CAS 是 CPU 指令级别的原子操作，不是通过 JVM 锁实现的。
 *            硬件保证"比较"和"交换"这两步之间不会插入其他 CPU 指令。
 *
 * 常见 Atomic 类：
 *   AtomicInteger  — int 的原子版本
 *   AtomicLong     — long 的原子版本
 *   AtomicBoolean  — boolean 的原子版本
 *   AtomicReference — 任意对象的原子版本
 *   LongAdder      — 高并发下比 AtomicLong 更快的累加器（JDK8+）
 */
public class AtomicIntegerDemo {

    private static final AtomicInteger stock = new AtomicInteger(100);  // 原子库存
    private static final AtomicInteger soldCount = new AtomicInteger(0); // 原子卖出计数

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== Demo 5-4: AtomicInteger 无锁修复超卖 ==========\n");

        // ===== 场景 1: incrementAndGet() — 简单累加 =====
        testSimpleIncrement();

        stock.set(100);
        soldCount.set(0);

        // ===== 场景 2: compareAndSet() — 手动 CAS 扣库存 =====
        testCompareAndSet();

        stock.set(100);
        soldCount.set(0);

        // ===== 场景 3: getAndUpdate() — Lambda 原子更新 =====
        testGetAndUpdate();

        System.out.println("\n========== Demo 5-4 完成 ==========");
    }

    /**
     * 场景 1: incrementAndGet() — 最简单的原子累加
     *
     * AtomicInteger 提供了一系列自增/自减方法，每个都是原子的：
     *   incrementAndGet()  — ++i（先加再返回）
     *   getAndIncrement()  — i++（先返回再加）
     *   decrementAndGet()  — --i
     *   getAndDecrement()  — i--
     *   addAndGet(n)       — i += n
     *
     * 这些方法底层都调用了 CAS（具体是 Unsafe.compareAndSwapInt）
     */
    private static void testSimpleIncrement() throws InterruptedException {
        System.out.println("【场景 1】incrementAndGet() — 原子累加\n");

        // 100个线程各执行1000次累加，最终结果应该是 100 * 1000 = 100000
        int threadCount = 100;
        int loopCount = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < loopCount; j++) {
                    counter.incrementAndGet();  // 原子操作，不加锁也安全
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        System.out.println("  期望值:     " + (threadCount * loopCount));
        System.out.println("  实际值:     " + counter.get());
        System.out.println("  结果验证:   " + (counter.get() == threadCount * loopCount ? "✅ 原子操作正确" : "❌ 异常"));
        System.out.println("  解释: incrementAndGet() 底层用 CAS 无限循环重试，保证最终结果正确\n");
    }

    /**
     * 场景 2: compareAndSet() — 手动 CAS 扣减库存
     *
     * 这是最原始的 CAS 用法，自己写 while 循环重试：
     *   1. 读取当前库存 current
     *   2. 如果 current <= 0，说明卖完了，退出
     *   3. 调用 compareAndSet(current, current - 1)
     *      - 成功 → 库存从 current 变成 current-1，卖出+1
     *      - 失败 → 说明其他线程改了库存，回到第1步重试
     *
     * 关键点：CAS 的"自旋"——失败后不停重试，这也是 CPU 空转的代价
     *         在高竞争下 CAS 自旋次数多，不如加锁（synchronized 会让线程休眠）
     */
    private static void testCompareAndSet() throws InterruptedException {
        System.out.println("【场景 2】compareAndSet() — 手动 CAS 扣库存（200线程并发）\n");

        int threadCount = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                // CAS 自旋：不断尝试，直到成功或库存为0
                while (true) {
                    int current = stock.get();           // 1. 读取当前库存
                    if (current <= 0) {
                        break;                           // 2. 库存没了，退出
                    }
                    // 3. 尝试 CAS：期望值=current，新值=current-1
                    if (stock.compareAndSet(current, current - 1)) {
                        soldCount.incrementAndGet();     // 4. 成功！卖出+1
                        break;
                    }
                    // 5. 失败 → 其他线程抢先改了库存 → 回到第1步重试
                }
                latch.countDown();
            }, "买家-" + i).start();
        }

        latch.await();
        System.out.println("  最终库存:  " + stock.get());
        System.out.println("  卖出数量:  " + soldCount.get());
        System.out.println("  结果验证:  " + (stock.get() == 0 && soldCount.get() == 100 ? "✅ 无超卖" : "❌ 异常"));
        System.out.println("  解释: compareAndSet(期望值, 新值) 是原子的，只有当前库存等于期望值时才更新\n");
    }

    /**
     * 场景 3: getAndUpdate() — JDK8 的 Lambda 简化写法
     *
     * JDK8 之后提供了更简洁的原子更新方式：
     *   getAndUpdate(UnaryOperator) — 用 Lambda 表达更新逻辑
     *   updateAndGet(UnaryOperator) — 同上，返回更新后的值
     *
     * 底层还是 CAS 自旋，但代码更简洁，不用手写 while 循环
     *
     * ⚠️ 重要：Lambda 内部必须是纯函数（无副作用）！
     *   CAS 重试时会多次调用 Lambda，如果里面有 incrementAndGet() 等副作用操作，
     *   重试几次就会多累加几次，导致数据错误。
     *   正确做法：Lambda 只计算新值，副作用操作放在 Lambda 外面。
     */
    private static void testGetAndUpdate() throws InterruptedException {
        System.out.println("【场景 3】getAndUpdate() — Lambda 简化 CAS 写法\n");

        int threadCount = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                // getAndUpdate 返回的是旧值（更新前的值）
                int oldValue = stock.getAndUpdate(current ->
                    current > 0 ? current - 1 : current  // Lambda：纯函数，只计算新值
                );
                // 副作用操作放在外面：只有旧值 > 0 才说明真正扣减成功
                if (oldValue > 0) {
                    soldCount.incrementAndGet();
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        System.out.println("  最终库存:  " + stock.get());
        System.out.println("  卖出数量:  " + soldCount.get());
        System.out.println("  结果验证:  " + (stock.get() == 0 && soldCount.get() == 100 ? "✅ 无超卖" : "❌ 异常"));
        System.out.println("  解释: Lambda 只做纯计算，副作用（计数）放外面，避免 CAS 重试导致重复累加\n");
    }
}