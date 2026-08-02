package com.livetix.demo;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo 7: SQL 原子操作 + 乐观锁——数据库层并发控制
 *
 * 一、为什么要数据库层并发控制？
 *
 *   Java锁（synchronized/ReentrantLock） → 只能锁住当前 JVM 进程
 *   Redis 分布式锁（SET NX）             → 能锁住多个 JVM，但 Redis 可能故障
 *   SQL 原子操作 + 乐观锁                 → 最后一道防线，数据库自己保证
 *
 *   多台服务器部署时，JVM 锁互不感知：
 *   服务器A: synchronized(this) { stock--; }  ← 锁住 A 的 this
 *   服务器B: synchronized(this) { stock--; }  ← 锁住 B 的 this（和 A 无关！）
 *   结果：A 和 B 同时扣库存 → 超卖！
 *
 *   所以 SQL 层的原子操作是分布式环境下的最终保障。
 *
 * 二、LiveTix 项目的两种 SQL 原子操作
 *
 *   1. 库存扣减（ShowMapper.deductStock）：
 *      UPDATE t_show SET available_stock = available_stock - #{quantity}
 *      WHERE id = #{showId} AND available_stock >= #{quantity}
 *                       ↑ 这就是乐观锁！库存不够时影响行数=0
 *
 *   2. 余额扣减（UserMapper.deductBalance）：
 *      UPDATE t_user SET balance = balance - #{amount}
 *      WHERE id = #{userId} AND balance >= #{amount}
 *                       ↑ 同样的模式，余额不够时影响行数=0
 *
 * 三、乐观锁 vs 悲观锁
 *
 *   乐观锁：假设不会冲突，更新时检查（WHERE 条件）
 *     优点：不加锁，并发高
 *     缺点：冲突多时重试次数多
 *     适用：读多写少（如库存扣减，大部分请求库存充足）
 *
 *   悲观锁：假设会冲突，先锁住再操作（SELECT ... FOR UPDATE）
 *     优点：不会冲突，不需要重试
 *     缺点：加锁，并发低
 *     适用：写多读少（如余额扣减，避免重复扣款）
 *
 * 面试点：MySQL 的 UPDATE 语句本身是行级锁，为什么还要加 WHERE 条件？
 *   答：不加 WHERE 的 UPDATE 也能锁住行，但无法感知"库存是否足够"。
 *       加了 WHERE available_stock >= quantity 后，库存不够时返回 0 行，
 *       业务代码根据影响行数判断是否成功，这才是乐观锁的核心。
 */
public class SqlAtomicDemo {

    // ========== 模拟数据库中的一行数据 ==========
    // 场景1: 不带乐观锁的库存（模拟普通 UPDATE）
    private static int stockWithoutLock = 100;
    private static final AtomicInteger soldWithoutLock = new AtomicInteger(0);

    // 场景2: 带乐观锁的库存（模拟 WHERE stock >= quantity）
    // 用 AtomicInteger 模拟数据库行级锁：一次只有一个线程能 CAS 成功
    private static final AtomicInteger stockWithLock = new AtomicInteger(100);
    private static final AtomicInteger soldWithLock = new AtomicInteger(0);

    // 场景3: 版本号乐观锁
    private static final AtomicInteger stockVersion = new AtomicInteger(100);
    private static final AtomicInteger version = new AtomicInteger(1);    // 版本号
    private static final AtomicInteger soldVersion = new AtomicInteger(0);

    // 场景4: 悲观锁模拟（SELECT FOR UPDATE）
    private static int stockPessimistic = 100;
    private static final Object dbRowLock = new Object();  // 模拟行锁
    private static final AtomicInteger soldPessimistic = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== Demo 7: SQL 原子操作 + 乐观锁 ==========\n");

        testWithoutLock();
        testWithOptimisticLock();
        testVersionOptimisticLock();
        testPessimisticLock();
        summary();

        System.out.println("\n========== Demo 7 完成 ==========");
    }

    /**
     * 场景 1: 不带乐观锁——先查再改（错误示范）
     *
     * 模拟这种代码：
     *   int stock = select stock from t_show where id = 1;  // 1. 查询
     *   if (stock >= quantity) {
     *       update t_show set stock = stock - quantity;     // 2. 更新
     *   }
     *
     * 问题：查询和更新之间，另一个线程可能已经把库存扣完了
     *       这就是"读-改-写"竞态条件在数据库层面的体现
     */
    private static void testWithoutLock() throws InterruptedException {
        System.out.println("【场景 1】不带乐观锁——先查再改（超卖复现）\n");

        int threadCount = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                // 模拟"先查再改"——这就是没有乐观锁的代码
                if (stockWithoutLock > 0) {         // 第1步：查询（非原子）
                    // 模拟网络延迟，让竞态条件更容易出现
                    try { Thread.sleep(1); } catch (InterruptedException e) { }
                    stockWithoutLock--;              // 第2步：更新（非原子）
                    soldWithoutLock.incrementAndGet();
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        System.out.println("  最终库存:  " + stockWithoutLock);
        System.out.println("  卖出数量:  " + soldWithoutLock.get());
        System.out.println("  结果:  " + (stockWithoutLock == 0 && soldWithoutLock.get() == 100
                ? "✅ 正常" : "❌ 超卖！"));
        System.out.println("  解释: 查询和更新不是原子操作，多线程交叉执行导致超卖\n");
    }

    /**
     * 场景 2: 乐观锁——WHERE 条件（LiveTix 实际方案）
     *
     * 模拟 SQL：
     *   UPDATE t_show SET available_stock = available_stock - 1
     *   WHERE id = 1 AND available_stock >= 1
     *
     * 关键：数据库的 UPDATE 语句是原子操作！
     *       MySQL 的 InnoDB 引擎会对这行加行级锁（X 锁），
     *       同一时刻只有一个 UPDATE 能执行，其他排队等待。
     *
     * 代码中判断影响行数：
     *   int rows = mapper.deductStock(showId, quantity);
     *   if (rows == 0) → 库存不足，扣减失败
     *   if (rows == 1) → 扣减成功
     *
     * 这里用 AtomicInteger.compareAndSet 模拟数据库行级原子 UPDATE
     */
    private static void testWithOptimisticLock() throws InterruptedException {
        System.out.println("【场景 2】乐观锁——WHERE stock >= quantity（LiveTix 方案）\n");

        int threadCount = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                // 模拟 SQL: UPDATE ... WHERE stock >= 1
                // compareAndSet 模拟数据库的原子行级 UPDATE
                boolean success = false;
                while (!success) {
                    int current = stockWithLock.get();
                    if (current <= 0) break;  // 库存没了
                    // 尝试 CAS 扣减（相当于 UPDATE WHERE stock >= quantity）
                    success = stockWithLock.compareAndSet(current, current - 1);
                }
                if (success) {
                    soldWithLock.incrementAndGet();
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        System.out.println("  最终库存:  " + stockWithLock.get());
        System.out.println("  卖出数量:  " + soldWithLock.get());
        System.out.println("  结果:  " + (stockWithLock.get() == 0 && soldWithLock.get() == 100
                ? "✅ 无超卖" : "❌ 异常"));
        System.out.println("  解释: 数据库 UPDATE 是原子操作，WHERE 条件保证库存不足时影响0行\n");
        System.out.println("  对应 LiveTix 代码: ShowMapper.deductStock() ——");
        System.out.println("    UPDATE t_show SET available_stock = available_stock - #{quantity}");
        System.out.println("    WHERE id = #{showId} AND available_stock >= #{quantity}\n");
    }

    /**
     * 场景 3: 版本号乐观锁——另一种常见的乐观锁实现
     *
     * 表结构：
     *   CREATE TABLE t_product (
     *     id BIGINT,
     *     stock INT,
     *     version INT  -- 版本号，每次更新 +1
     *   );
     *
     * SQL：
     *   UPDATE t_product SET stock = stock - 1, version = version + 1
     *   WHERE id = 1 AND version = #{oldVersion}
     *
     * 如果另一个线程已经更新了，version 变了 → WHERE 条件不匹配 → 影响0行
     * 业务代码判断影响行数：0 → 重试，1 → 成功
     *
     * 适合场景：需要更新多个字段，且更新互相依赖
     * LiveTix 没用这个，因为库存扣减只需要 WHERE stock >= quantity 就够了
     */
    private static void testVersionOptimisticLock() throws InterruptedException {
        System.out.println("【场景 3】版本号乐观锁——version 字段\n");

        int threadCount = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                boolean success = false;
                while (!success) {
                    int currentStock = stockVersion.get();
                    int currentVersion = version.get();
                    if (currentStock <= 0) break;

                    // 模拟 SQL: UPDATE ... SET stock = stock - 1, version = version + 1
                    //            WHERE id = 1 AND version = oldVersion
                    // 同时更新库存和版本号，用一个 CAS 保证原子性
                    // 真实数据库里这是单条 UPDATE，天然原子；这里用两个 CAS 模拟
                    if (stockVersion.compareAndSet(currentStock, currentStock - 1)
                            && version.compareAndSet(currentVersion, currentVersion + 1)) {
                        success = true;
                        soldVersion.incrementAndGet();
                    }
                    // 如果版本号 CAS 失败，说明被其他线程抢先了 → 重试
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        System.out.println("  最终库存:  " + stockVersion.get());
        System.out.println("  最终版本:  " + version.get());
        System.out.println("  卖出数量:  " + soldVersion.get());
        System.out.println("  结果:  " + (stockVersion.get() == 0 && soldVersion.get() == 100
                ? "✅ 无超卖" : "❌ 异常"));
        System.out.println("  解释: 版本号每次更新+1，旧版本号的 UPDATE 无法匹配 → 并发安全\n");
    }

    /**
     * 场景 4: 悲观锁——SELECT ... FOR UPDATE
     *
     * SQL：
     *   BEGIN;  -- 开启事务
     *   SELECT stock FROM t_product WHERE id = 1 FOR UPDATE;  -- 加行级排他锁
     *   -- 检查库存是否足够...
     *   UPDATE t_product SET stock = stock - 1 WHERE id = 1;
     *   COMMIT;  -- 提交事务，释放锁
     *
     * 特点：
     *   当前事务没提交前，其他事务的 SELECT ... FOR UPDATE 会阻塞等待
     *   相当于在数据库层面加了 synchronized
     *
     * 缺点：
     *   并发低（所有线程排队等锁）
     *   容易死锁（多个 FOR UPDATE 互相等待）
     *
     * LiveTix 不用这个，因为秒杀场景追求高并发，乐观锁更适合
     */
    private static void testPessimisticLock() throws InterruptedException {
        System.out.println("【场景 4】悲观锁——SELECT ... FOR UPDATE\n");

        int threadCount = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                synchronized (dbRowLock) {  // 模拟 SELECT ... FOR UPDATE 的行锁
                    if (stockPessimistic > 0) {
                        stockPessimistic--;
                        soldPessimistic.incrementAndGet();
                    }
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        System.out.println("  最终库存:  " + stockPessimistic);
        System.out.println("  卖出数量:  " + soldPessimistic.get());
        System.out.println("  结果:  " + (stockPessimistic == 0 && soldPessimistic.get() == 100
                ? "✅ 无超卖" : "❌ 异常"));
        System.out.println("  解释: 所有线程排队等锁，并发度低，但绝对安全\n");
    }

    /**
     * 总结对比
     */
    private static void summary() {
        System.out.println("========== 总结对比 ==========");
        System.out.println();
        System.out.println("  方案           | 原理                     | 并发度 | 适用场景");
        System.out.println("  ───────────────┼──────────────────────────┼───────┼──────────────");
        System.out.println("  Java锁         | JVM 内置锁               | 中     | 单机部署");
        System.out.println("  Redis分布式锁  | SET NX 互斥               | 高     | 分布式防重");
        System.out.println("  SQL乐观锁      | WHERE 条件（库存 >= 数量） | 最高   | 库存扣减（最终防线）");
        System.out.println("  SQL悲观锁      | SELECT FOR UPDATE        | 低     | 余额扣减（避免重复）");
        System.out.println("  版本号乐观锁   | WHERE version = oldVer    | 高     | 多字段更新");
        System.out.println();
        System.out.println("  LiveTix 三道防线：");
        System.out.println("    1. Redis Lua 预扣库存（最快，拦截 99% 无效请求）");
        System.out.println("    2. Redis SET NX 防重锁（防同一用户重复下单）");
        System.out.println("    3. SQL WHERE available_stock >= quantity（最终防线，兜底）");
    }
}