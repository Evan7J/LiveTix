package com.livetix.demo;

/**
 * Demo 8-5: 面试话术总结——从项目介绍到高频追问的全套回答
 *
 * ============================================================
 * 以下是你面试时可以被问到的所有核心问题及标准回答。
 * 不是让你背诵，而是理解每个问题的考察点，用自己的话讲出来。
 * ============================================================
 */
public class InterviewTalkingPoints {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║           Demo 8-5: 面试话术总结——全套回答模板                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");

        projectIntro();
        seckillFlow();
        cacheThreeProblems();
        concurrencyControl();
        mqAndPeakShaving();
        databaseOptimisticLock();
        commonFollowUpQuestions();
        reverseQuestions();

        System.out.println("\n========== Demo 8-5 完成 ==========");
        System.out.println("\n🎉 恭喜！8 个 Demo 全部完成！");
        System.out.println("你现在已经具备了一个实习生的核心能力：");
        System.out.println("  1. 能讲清楚秒杀系统的完整链路");
        System.out.println("  2. 能解释缓存三兄弟的区别和解决方案");
        System.out.println("  3. 能手写多线程并发控制代码");
        System.out.println("  4. 能说出限流/防重/幂等的区别");
        System.out.println("  5. 能解释 SQL 乐观锁的原理");
        System.out.println("  6. 能讲清楚 RocketMQ 削峰填谷的作用");
        System.out.println("  7. 能回答大部分面试追问");
        System.out.println("\n剩下的就是把这些 Demo 的代码自己敲一遍，");
        System.out.println("把每个方法的作用和设计思路讲给自己听。");
        System.out.println("面试时你的底气就来自于这些亲手写过的代码。");
    }

    // ================================================================
    //  1. 项目介绍
    // ================================================================

    /**
     * 面试官："介绍一下你的项目吧。"
     *
     * 考察点：能否在 30 秒内说清楚项目是什么、用了什么技术、解决了什么问题。
     * 不要罗列技术栈！要讲业务场景 + 技术挑战 + 解决方案。
     */
    private static void projectIntro() {
        System.out.println("═══ 1. 项目介绍 ═══\n");

        System.out.println("【一句话版本（30秒）】");
        System.out.println("  LiveTix 是一个演唱会票务秒杀系统。核心解决三个问题：");
        System.out.println("  1) 高并发下库存超卖——用 Redis Lua 原子预扣 + SQL 乐观锁双重保障");
        System.out.println("  2) 流量洪峰打垮数据库——用 RocketMQ 异步下单削峰填谷");
        System.out.println("  3) 缓存穿透/击穿/雪崩——用 NULL 占位、互斥锁、TTL 随机化三层保护\n");

        System.out.println("【详细版本（2分钟）】");
        System.out.println("  技术栈：SpringBoot 3.2 + MyBatis-Plus + Redis + RocketMQ + Sa-Token + Docker Compose");
        System.out.println("  后端：Java 17，前后端分离，Vue3 + Element Plus");
        System.out.println("  部署：Docker Compose 一键启动 6 个容器（MySQL、Redis、RocketMQ、Nginx 等）");
        System.out.println();
        System.out.println("  核心功能模块：");
        System.out.println("    - 用户系统：注册登录、RBAC 四角色权限、AES 加密身份证");
        System.out.println("    - 秒杀系统：Redis Lua 预扣库存 → MQ 异步下单 → DB 乐观锁写入");
        System.out.println("    - 支付退款：原子余额扣减、HMAC-SHA256 签名验证、退款审批流");
        System.out.println("    - 缓存体系：穿透/击穿/雪崩三层保护、TTL 随机化、互斥锁重建");
        System.out.println();
        System.out.println("  我主要负责：秒杀核心链路的设计与实现，包括并发控制、缓存策略、消息队列。\n");
    }

    // ================================================================
    //  2. 秒杀核心链路
    // ================================================================

    /**
     * 面试官："秒杀的核心流程是怎样的？"
     *
     * 考察点：能否把整个链路串起来讲清楚，每步为什么这么做。
     * 这是面试中最重要的一个问题，决定面试官对你的整体印象。
     */
    private static void seckillFlow() {
        System.out.println("═══ 2. 秒杀核心链路（最重要！）═══\n");

        System.out.println("  用户点击下单后，经过以下 6 步：\n");

        System.out.println("  第 1 步：令牌桶限流");
        System.out.println("    系统每秒只放行 500 个请求，超出的直接返回 429。");
        System.out.println("    为什么用令牌桶？固定窗口有边界问题，令牌桶更平滑。\n");

        System.out.println("  第 2 步：缓存查询（三层保护）");
        System.out.println("    先查 Redis 缓存，缓存没有查 DB 并回写。");
        System.out.println("    不存在的 key 缓存 NULL，防穿透；");
        System.out.println("    热点 key 用 SET NX 互斥锁重建，防击穿；");
        System.out.println("    TTL 加 ±20% 随机值，防雪崩。\n");

        System.out.println("  第 3 步：Redis Lua 原子预扣库存");
        System.out.println("    执行 Lua 脚本：GET 库存 → 判断是否足够 → DECRBY 扣减。");
        System.out.println("    整个过程在 Redis 单线程中执行，天然原子，不会超卖。");
        System.out.println("    库存不足直接返回『售罄』，拦截 99% 无效请求。\n");

        System.out.println("  第 4 步：用户防重锁");
        System.out.println("    Redis SET NX 加锁 10 秒，防止用户手抖重复下单。");
        System.out.println("    10 秒后自动过期，允许用户重试。\n");

        System.out.println("  第 5 步：RocketMQ 异步下单");
        System.out.println("    asyncSend 发送消息到 MQ，用户立即拿到『排队中』响应。");
        System.out.println("    MQ 消费者异步写入数据库，实现削峰填谷。");
        System.out.println("    发送失败回调 onException，回滚 Redis 库存。\n");

        System.out.println("  第 6 步：DB 乐观锁最终防线");
        System.out.println("    SQL: UPDATE t_show SET stock = stock - N WHERE id = ? AND stock >= N");
        System.out.println("    影响行数 = 0 说明库存不足，回滚 Redis 预扣。");
        System.out.println("    这是最后一道防线，即使 Redis 数据不一致，DB 也保证不超卖。\n");
    }

    // ================================================================
    //  3. 缓存三兄弟
    // ================================================================

    /**
     * 面试官："缓存穿透、击穿、雪崩是什么？怎么解决？"
     *
     * 考察点：这是缓存必考题，要能脱口而出。
     */
    private static void cacheThreeProblems() {
        System.out.println("═══ 3. 缓存三兄弟（必考）═══\n");

        System.out.println("  ┌──────────┬──────────────────────┬──────────────────────────┐");
        System.out.println("  │ 问题      │ 原因                  │ 解决方案                   │");
        System.out.println("  ├──────────┼──────────────────────┼──────────────────────────┤");
        System.out.println("  │ 穿透      │ 查不存在的数据         │ NULL 占位，TTL 60秒       │");
        System.out.println("  │ 击穿      │ 热点 key 过期瞬间      │ SET NX 互斥锁 + Double Check │");
        System.out.println("  │ 雪崩      │ 大量 key 同时过期      │ TTL ±20% 随机值           │");
        System.out.println("  └──────────┴──────────────────────┴──────────────────────────┘\n");

        System.out.println("  穿透追问：为什么 NULL 也要缓存 60 秒？");
        System.out.println("    答：不缓存的话攻击者每次换个 ID 都能穿透。");
        System.out.println("        缓存了 NULL，每个 ID 只穿透一次。");
        System.out.println("        60 秒是平衡：太短防不住，太长数据不一致。\n");

        System.out.println("  击穿追问：为什么需要 Double Check？");
        System.out.println("    答：线程 A 拿到锁去查 DB，线程 B 等锁释放。");
        System.out.println("        如果 B 拿到锁后不 Double Check，会再查一次 DB。");
        System.out.println("        Double Check 保证只有第一个线程查 DB。\n");

        System.out.println("  雪崩追问：为什么是 ±20% 而不是更大？");
        System.out.println("    答：20% 足够分散过期时间（300 秒 TTL → 300-360 秒），");
        System.out.println("        同时保证数据不会过期太久。如果 ±50%，数据可能过期 150 秒。\n");
    }

    // ================================================================
    //  4. 并发控制
    // ================================================================

    /**
     * 面试官："限流、防重、幂等有什么区别？"
     *
     * 考察点：三个概念容易混淆，要能清楚区分。
     */
    private static void concurrencyControl() {
        System.out.println("═══ 4. 限流 / 防重 / 幂等 ═══\n");

        System.out.println("  ┌────────┬──────────────┬──────────────┬──────────────────────┐");
        System.out.println("  │ 概念    │ 保护对象      │ 粒度          │ 实现                   │");
        System.out.println("  ├────────┼──────────────┼──────────────┼──────────────────────┤");
        System.out.println("  │ 限流    │ 系统整体      │ 全局          │ 令牌桶 / 滑动窗口       │");
        System.out.println("  │ 防重    │ 单个用户      │ 用户+场次      │ Redis SET NX EX 10     │");
        System.out.println("  │ 幂等    │ 单次请求      │ 请求ID        │ Redis SET NX / DB唯一索引 │");
        System.out.println("  └────────┴──────────────┴──────────────┴──────────────────────┘\n");

        System.out.println("  举例：一个用户下单的完整流程");
        System.out.println("    1. 先过限流：系统每秒只放行 500 个请求（不管你是谁）");
        System.out.println("    2. 再过防重：同一用户 10 秒内不能重复下单（防手抖）");
        System.out.println("    3. 最后幂等：同一 requestId 只处理一次（防网络重传）\n");
    }

    // ================================================================
    //  5. MQ 削峰填谷
    // ================================================================

    /**
     * 面试官："为什么用消息队列？不用行不行？"
     *
     * 考察点：是否理解异步处理的本质价值。
     */
    private static void mqAndPeakShaving() {
        System.out.println("═══ 5. RocketMQ 削峰填谷 ═══\n");

        System.out.println("  不用 MQ 的问题：");
        System.out.println("    10000 人同时下单 → 10000 个请求直接写 DB → DB 连接池打满 → 系统崩溃");
        System.out.println("    每个请求写 DB 需要 200ms → 10000 个请求排队 = 2000 秒 = 33 分钟");
        System.out.println("    用户等 33 分钟？不可能。\n");

        System.out.println("  用 MQ 之后：");
        System.out.println("    10000 个请求 → Redis 预扣库存（< 5ms）→ 发 MQ 消息 → 立即返回『排队中』");
        System.out.println("    MQ 队列堆积 10000 条消息 → 10 个消费者慢慢处理 → 不压 DB");
        System.out.println("    用户 50ms 内拿到响应，体验好。\n");

        System.out.println("  为什么是异步发送（asyncSend）而不是同步？");
        System.out.println("    同步：发完等 Broker 确认 → 多等 10-50ms，积少成多");
        System.out.println("    异步：发完就走，Broker 确认后回调 onSuccess/onException");
        System.out.println("    失败处理：onException 里回滚 Redis 库存 + 释放防重锁\n");

        System.out.println("  延迟消息的作用：");
        System.out.println("    下单后发送 15 分钟延迟消息 → 15 分钟后检查支付状态");
        System.out.println("    未支付 → 取消订单 + 回滚库存");
        System.out.println("    同时还有 @Scheduled 定时任务每 60 秒扫描，双保险。\n");
    }

    // ================================================================
    //  6. 数据库乐观锁
    // ================================================================

    /**
     * 面试官："数据库层面的并发控制怎么做？"
     *
     * 考察点：乐观锁 vs 悲观锁的选择。
     */
    private static void databaseOptimisticLock() {
        System.out.println("═══ 6. DB 乐观锁——最终防线 ═══\n");

        System.out.println("  SQL: UPDATE t_show SET stock = stock - #{quantity}");
        System.out.println("       WHERE id = #{showId} AND stock >= #{quantity}\n");

        System.out.println("  为什么这是最后一道防线？");
        System.out.println("    Redis 可能挂了，库存数据不一致。");
        System.out.println("    MQ 消费可能重复。");
        System.out.println("    只有 DB 的 WHERE 条件能保证最终一致性。\n");

        System.out.println("  乐观锁 vs 悲观锁：");
        System.out.println("    乐观锁（WHERE stock >= quantity）：");
        System.out.println("      不加锁，更新时检查，冲突了重试。");
        System.out.println("      适合秒杀场景（读多写少，冲突概率低）。");
        System.out.println("    悲观锁（SELECT FOR UPDATE）：");
        System.out.println("      先锁住行，再操作，所有人排队等。");
        System.out.println("      适合余额扣减（避免重复扣款，冲突概率高）。\n");
    }

    // ================================================================
    //  7. 常见追问
    // ================================================================

    /**
     * 面试官可能追问的 10 个问题
     */
    private static void commonFollowUpQuestions() {
        System.out.println("═══ 7. 10 个高频面试追问 ═══\n");

        System.out.println("  Q1: Redis 挂了怎么办？");
        System.out.println("  A:  DB 乐观锁是最后防线。Redis 挂了系统降级，直接走 DB 扣库存。");
        System.out.println("     同时 Sentinel 哨兵 + 主从复制保证 Redis 高可用。\n");

        System.out.println("  Q2: RocketMQ 挂了怎么办？");
        System.out.println("  A:  发送失败回调 onException 回滚 Redis 库存。");
        System.out.println("     同时 @Scheduled 定时任务兜底，扫描超时订单。\n");

        System.out.println("  Q3: 为什么用 Lua 脚本而不是 Redis 事务（MULTI/EXEC）？");
        System.out.println("  A:  Redis 事务不支持条件判断（if stock >= quantity），Lua 可以。");
        System.out.println("      Lua 脚本在 Redis 单线程中执行，天然原子，比事务更灵活。\n");

        System.out.println("  Q4: 为什么不用 synchronized 加锁？");
        System.out.println("  A:  synchronized 只能锁住当前 JVM，多台服务器部署时互不干扰。");
        System.out.println("      分布式环境必须用 Redis 分布式锁或 DB 乐观锁。\n");

        System.out.println("  Q5: TTL 为什么是 300-360 秒？");
        System.out.println("  A:  300 秒够短，数据变更能较快感知；360 秒够长，缓存命中率高。");
        System.out.println("      300-360 的随机范围避免大量 key 同时过期。\n");

        System.out.println("  Q6: 库存扣减后，MQ 消费失败怎么回滚？");
        System.out.println("  A:  onException 回调里 INCR 回滚 Redis 库存 + DELETE 释放防重锁。");
        System.out.println("      DB 层没写入，所以不需要回滚。\n");

        System.out.println("  Q7: 延迟消息的延迟级别怎么选？");
        System.out.println("  A:  RocketMQ 只支持 18 个固定延迟级别，不是任意时间。");
        System.out.println("      15 分钟 → 选 delayLevel=14（10 分钟）或 15（20 分钟），");
        System.out.println("      我们选 14，略微提前检查，宁可早取消也不晚取消。\n");

        System.out.println("  Q8: 缓存和数据库的双写一致性怎么保证？");
        System.out.println("  A:  先更新数据库，再删除缓存（Cache Aside 模式）。");
        System.out.println("      秒杀场景库存只减不增，数据一致性要求不高，");
        System.out.println("      即使缓存有短暂不一致，DB 乐观锁也能兜底。\n");

        System.out.println("  Q9: 你的系统能支持多少 QPS？");
        System.out.println("  A:  单机 Redis 预扣库存可以达到 10 万+ QPS。");
        System.out.println("      实际瓶颈在 DB，通过 MQ 削峰后 DB 写入 QPS 控制在 100 以内。");
        System.out.println("      整体系统 QPS 取决于 Redis 性能，远高于 DB 瓶颈。\n");

        System.out.println("  Q10: 如果让你重新设计，你会改进什么？");
        System.out.println("  A:   1) 库存分段：热点演出库存分多段，减少 Redis 单 key 热点。");
        System.out.println("       2) 降级熔断：加 Sentinel 熔断，Redis 挂了自动降级到 DB 直接扣。");
        System.out.println("       3) 读写分离：MySQL 主从复制，读走从库，减轻主库压力。");
        System.out.println("       4) 前端限流：按钮置灰 + 倒计时，减少无效请求到达后端。\n");
    }

    // ================================================================
    //  8. 反问面试官
    // ================================================================

    /**
     * 面试官："你有什么想问我的吗？"
     *
     * 考察点：是否对公司有真实兴趣，是否思考过自己的职业发展。
     */
    private static void reverseQuestions() {
        System.out.println("═══ 8. 反问面试官（加分项）═══\n");

        System.out.println("  推荐问题（按优先级排序）：\n");

        System.out.println("  1. 『团队目前的技术栈是什么？有没有用到消息队列或分布式缓存？』");
        System.out.println("     → 展示你对技术的关注，同时了解团队技术氛围。\n");

        System.out.println("  2. 『实习生入职后会有 mentor 带吗？一般会安排什么类型的任务？』");
        System.out.println("     → 展示你想快速上手，不是来混日子的。\n");

        System.out.println("  3. 『团队在用的中间件（Redis/MQ）有没有遇到过什么坑？』");
        System.out.println("     → 展示你对中间件的兴趣，同时暗示你有相关经验。\n");

        System.out.println("  4. 『公司对实习生的培养计划是什么样的？有没有转正机会？』");
        System.out.println("     → 展示你希望长期发展，不是短期实习。\n");

        System.out.println("  不要问的问题：");
        System.out.println("    ❌ 加班多不多？（会被认为怕吃苦）");
        System.out.println("    ❌ 薪资多少？（HR 面再问，技术面问不合适）");
        System.out.println("    ❌ 公司做什么的？（显得你完全没做功课）\n");
    }
}