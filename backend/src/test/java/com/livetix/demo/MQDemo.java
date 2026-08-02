package com.livetix.demo;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo 6: RocketMQ 异步下单——消息队列核心概念
 *
 * 一、为什么秒杀系统需要消息队列？
 *
 * 用户请求 → Controller → Service → 写DB → 返回
 *   这就是同步处理，问题是：
 *   1. 用户要等写DB完成才能看到结果（响应慢，3-5秒）
 *   2. 1000个请求同时写DB → 连接池打满 → 系统崩溃
 *   3. 请求和DB处理强耦合，一个慢就全慢
 *
 * 引入消息队列后：
 *   用户请求 → Controller → Redis预扣库存 → 发MQ → 立即返回"排队中"（响应快，<100ms）
 *                                          ↓
 *                                    Consumer慢慢消费 → 写DB
 *
 *   这就是削峰填谷：MQ把瞬间的流量洪峰变成平滑的消费流
 *
 * 二、RocketMQ 核心概念
 *
 *   Producer（生产者） → 发消息的一方，LiveTix 里是 OrderMessageProducer
 *   Consumer（消费者） → 收消息的一方，LiveTix 里是 OrderMessageConsumer
 *   Topic（主题）      → 消息分类，如 "livetix-order-topic"
 *   Tag（标签）        → Topic 下的子分类，如 "order-create"
 *   Message（消息）    → 传输的内容，JSON 字符串
 *   Broker（代理）     → 存储和转发消息的服务器
 *
 * 三、发送消息的三种方式
 *
 *   syncSend（同步）  → 发完等Broker确认，最可靠但最慢
 *   asyncSend（异步） → 发完不等，Broker确认后回调，LiveTix 用这个
 *   sendOneWay（单向）→ 发完就走，不关心结果，最快但可能丢
 *
 * 四、延迟消息（定时取消订单的关键）
 *
 *   RocketMQ 支持 18 个延迟级别：
 *   1s/5s/10s/30s/1m/2m/3m/4m/5m/6m/7m/8m/9m/10m/20m/30m/1h/2h
 *
 * 五、消费模式
 *
 *   CLUSTERING（集群消费）→ 一条消息只被一个消费者消费（默认，LiveTix 用这个）
 *   BROADCASTING（广播消费）→ 一条消息被所有消费者消费
 *
 * 面试点：RocketMQ 和 Kafka 的区别？
 *   RocketMQ: 阿里出品，延迟消息原生支持，事务消息，更适合电商场景
 *   Kafka:    大数据吞吐量更高，但延迟消息不支持，更适日志/流处理
 */
public class MQDemo {

    // ========== 用 BlockingQueue 模拟 RocketMQ Broker ==========
    // 真实 RocketMQ 里这是一个独立的服务器进程，这里用内存队列模拟
    private static final BlockingQueue<String> orderQueue = new LinkedBlockingQueue<>(100);
    private static final BlockingQueue<DelayedMessage> delayQueue = new DelayQueue<>();

    private static final AtomicInteger orderIdGenerator = new AtomicInteger(1000);
    private static final AtomicInteger dbStock = new AtomicInteger(100);     // 模拟数据库库存
    private static final AtomicInteger mqSuccessCount = new AtomicInteger(0);
    private static final AtomicInteger mqFailCount = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== Demo 6: RocketMQ 异步下单（模拟） ==========\n");

        // ===== 场景 1: 对比同步 vs 异步处理 =====
        testSyncVsAsync();

        // ===== 场景 2: 削峰填谷 —— 模拟秒杀流量 =====
        testPeakShaving();

        // ===== 场景 3: 延迟消息 —— 订单超时取消 =====
        testDelayedMessage();

        System.out.println("\n========== Demo 6 完成 ==========");
    }

    /**
     * 场景 1: 同步处理 vs 异步处理（MQ）
     *
     * 同步：用户等写DB完成 → 慢
     * 异步：用户等Redis扣库存 → 快，DB由MQ慢慢写
     */
    private static void testSyncVsAsync() throws InterruptedException {
        System.out.println("【场景 1】同步 vs 异步——下单速度对比\n");

        // 启动消费者线程（模拟MQ消费者）
        Thread consumer = new Thread(MQDemo::consumeOrders, "MQ消费者");
        consumer.setDaemon(true);
        consumer.start();

        // 模拟一个用户下单
        long start = System.currentTimeMillis();

        // 异步方式（LiveTix 实际流程）
        String result = asyncOrder(1L, 50L, 2);
        long asyncTime = System.currentTimeMillis() - start;

        System.out.println("  异步下单: " + result + " (耗时 " + asyncTime + "ms)");
        System.out.println("  解释: 只做了Redis预扣 + 发MQ，用户立即拿到响应");
        System.out.println("        写DB等耗时操作由 MQ 消费者异步完成\n");

        Thread.sleep(500); // 等消费者处理完
    }

    /**
     * 模拟异步下单流程（LiveTix 实际链路）
     *
     * 1. Redis 预扣库存（Lua 脚本原子操作）→ 快
     * 2. 发送 MQ 消息 → 快
     * 3. 立即返回"排队中" → 快
     *
     * 4. MQ消费者 → 写DB → 创建订单（慢，但用户不感知）
     */
    private static String asyncOrder(Long userId, Long showId, int quantity) {
        // 1. Redis 预扣库存（模拟，实际用 Lua 脚本）
        int currentStock = dbStock.addAndGet(-quantity);
        if (currentStock < 0) {
            dbStock.addAndGet(quantity); // 回滚
            mqFailCount.incrementAndGet();
            return "FAIL: 库存不足";
        }

        // 2. 发送 MQ 消息（异步发送）
        String message = String.format(
                "{\"userId\":%d,\"showId\":%d,\"quantity\":%d}", userId, showId, quantity);
        boolean sent = orderQueue.offer(message);  // 非阻塞发送

        if (!sent) {
            dbStock.addAndGet(quantity); // 回滚库存
            mqFailCount.incrementAndGet();
            return "FAIL: 系统繁忙";
        }

        mqSuccessCount.incrementAndGet();
        return "OK: 排队中，请稍后查看";  // 立即返回，不等待DB写入
    }

    /**
     * MQ 消费者：从队列取消息，慢慢写DB
     */
    private static void consumeOrders() {
        while (true) {
            try {
                String message = orderQueue.poll(1, TimeUnit.SECONDS);
                if (message == null) continue;

                // 模拟写DB：耗时操作
                System.out.println("    [MQ消费者] 收到消息: " + message);
                Thread.sleep(200);  // 模拟DB写入耗时
                int orderId = orderIdGenerator.incrementAndGet();
                System.out.println("    [MQ消费者] 订单# " + orderId + " 创建成功（DB写入完成）");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 场景 2: 削峰填谷——100个请求瞬间涌入
     *
     * 没有MQ：100个请求直接打DB → 连接池撑爆
     * 有MQ：  100个请求瞬间进队列 → 消费者按自己的节奏慢慢处理
     */
    private static void testPeakShaving() throws InterruptedException {
        System.out.println("【场景 2】削峰填谷——100个并发请求 vs MQ 缓冲\n");

        dbStock.set(100);
        mqSuccessCount.set(0);
        mqFailCount.set(0);
        orderQueue.clear();

        long start = System.currentTimeMillis();

        // 模拟100个用户同时秒杀
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(20);

        for (int i = 1; i <= threadCount; i++) {
            final int userId = i;
            pool.execute(() -> {
                asyncOrder((long) userId, 50L, 1);
                latch.countDown();
            });
        }

        latch.await();
        long allSent = System.currentTimeMillis() - start;

        System.out.println("  100个请求全部发送完成: " + allSent + "ms");
        System.out.println("  发送成功: " + mqSuccessCount.get());
        System.out.println("  发送失败: " + mqFailCount.get());
        System.out.println("  队列堆积: " + orderQueue.size() + " 条");
        System.out.println("  解释: 100个请求瞬间入队，用户都在 < " + allSent + "ms 内拿到响应");
        System.out.println("        消费者按自己的速度慢慢写DB，不会被流量洪峰打垮\n");

        pool.shutdown();
        Thread.sleep(3000); // 等消费者消费完
    }

    /**
     * 场景 3: 延迟消息——订单超时自动取消
     *
     * LiveTix 流程：
     *   1. 用户下单成功 → 发送一条延迟消息（15分钟后投递）
     *   2. 15分钟后消费者收到 → 检查订单是否已支付
     *   3. 未支付 → 取消订单，回滚库存
     *   4. 已支付 → 什么都不做
     *
     * 同时还有 @Scheduled 定时任务做兜底（双保险）
     *
     * RocketMQ 的延迟级别（18级）：
     *   1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     *
     * 这里用 DelayQueue 模拟延迟消息
     */
    private static void testDelayedMessage() throws InterruptedException {
        System.out.println("【场景 3】延迟消息——订单超时自动取消\n");

        // 模拟创建订单，发送15分钟后的延迟取消消息
        // 实际中延迟15分钟，这里用3秒演示
        int orderId = orderIdGenerator.incrementAndGet();
        String orderNo = "LVT" + System.currentTimeMillis();

        // 发送延迟消息（3秒后投递）
        DelayedMessage delayedMsg = new DelayedMessage(
                "{\"orderId\":" + orderId + ",\"orderNo\":\"" + orderNo + "\"}",
                3000  // 3秒后投递（真实场景是15分钟）
        );
        delayQueue.put(delayedMsg);
        System.out.println("  订单 #" + orderId + " (" + orderNo + ") 创建成功");
        System.out.println("  延迟取消消息已发送，3秒后投递（模拟15分钟）\n");

        // 启动延迟消息消费者
        Thread delayConsumer = new Thread(() -> {
            while (true) {
                try {
                    DelayedMessage msg = delayQueue.take();  // 阻塞等到期
                    System.out.println("  [延迟消费者] 收到延迟消息: " + msg.getContent());
                    System.out.println("  [延迟消费者] 检查订单 #" + orderId + " 状态...");

                    // 模拟：订单未支付 → 取消
                    boolean paid = false;  // 假设未支付
                    if (!paid) {
                        dbStock.incrementAndGet();  // 回滚库存
                        System.out.println("  [延迟消费者] 订单 #" + orderId
                                + " 未支付，已取消，库存已回滚 (+1)");
                    } else {
                        System.out.println("  [延迟消费者] 订单 #" + orderId
                                + " 已支付，无需处理");
                    }
                    System.out.println("  当前库存: " + dbStock.get() + "\n");
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "延迟消息消费者");
        delayConsumer.setDaemon(true);
        delayConsumer.start();

        Thread.sleep(4000); // 等延迟消息被消费
    }

    /**
     * 延迟消息包装类（模拟 RocketMQ 的延迟消息）
     * 实现 Delayed 接口，让 DelayQueue 知道何时到期
     */
    static class DelayedMessage implements Delayed {
        private final String content;
        private final long expireTime;  // 到期时间戳（毫秒）

        public DelayedMessage(String content, long delayMs) {
            this.content = content;
            this.expireTime = System.currentTimeMillis() + delayMs;
        }

        public String getContent() { return content; }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(expireTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(this.expireTime, ((DelayedMessage) o).expireTime);
        }
    }
}