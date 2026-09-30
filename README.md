# LiveTix · 演出选座秒杀平台

一个覆盖选座、下单、支付、超时关单全链路的在线票务系统。核心要解决的问题是：**高并发抢票场景下，怎么既不超卖、又不让同一座位被两个人抢到。**

## 背景

票务秒杀和普通商品秒杀不太一样，多了一层"选座"的约束：

- 普通秒杀只要保证库存扣减正确
- 选座秒杀还要保证**同一个座位在同一时刻只能被一个人锁定**

而且座位是细粒度的——一场演出上千个座位，如果按"整场一把锁"来做，并发度会低得没法看。所以这个项目里很大一部分设计都围绕"怎么把锁的粒度做细，同时又不出现重复锁定"。

## 快速开始

需要 Docker Desktop。

```bash
git clone https://github.com/Evan7J/LiveTix.git
cd LiveTix
docker compose up -d
```

打开 http://localhost:3000 就能看到前台。

管理员后台在 http://localhost:3000/admin ，账号 `admin` / 密码 `admin123`。

> 数据库表结构和初始数据在 `backend/src/main/resources/db/init.sql`，容器启动时自动执行。

## 技术栈

| 模块 | 选型 |
|------|------|
| 后端 | Java 17 · Spring Boot 3.2.5 · MyBatis-Plus |
| 认证鉴权 | Sa-Token（Redis 存储会话 + RBAC 角色权限） |
| 缓存 / 分布式锁 | Redis 7 + Lua |
| 消息队列 | RocketMQ 2.3.1（异步下单 + 延迟消息） |
| 数据库 | MySQL 8 |
| 接口文档 | SpringDoc OpenAPI（Swagger UI） |
| 前端 | Vue 3 + Element Plus |
| 部署 | Docker Compose |

## 核心设计

### 1. 库存防超卖：Redis + Lua 原子预扣

最开始的实现是"先查再扣"，压测到 200 并发就开始出现超卖。原因是查和扣之间不是原子的，两个请求可能同时读到"还剩 1 张"。

改法是把**校验和扣减合并成一条 Lua 脚本**，在 Redis 内部一次执行完：

```lua
-- stock_deduct.lua 核心逻辑
local current = tonumber(redis.call('GET', stockKey))
if current == nil then
    current = tonumber(ARGV[2])   -- 首次访问用 DB 库存初始化
end
if current < quantity then
    return -1                      -- 库存不足
end
redis.call('SET', stockKey, remaining, 'EX', 86400)
```

Redis 单线程执行脚本，中间不会被其他命令插入，所以不存在竞态。同时把争抢从数据库行锁转移到了 Redis，规避了高并发下 InnoDB 行锁的排队开销。

配套还有一个 `stock_restore.lua`，专门处理回补。这里有个细节：**回补时必须先判断 key 是否存在**，不能直接 `INCRBY`——否则一个没预热过的演出会被凭空创建出库存键，下次秒杀就会读到脏数据。

### 2. 选座防冲突：座位级独立锁

每个座位一把锁，而不是整场一把大锁：

- 用 `livetix:seat:lock:{sessionId}:{seatId}` 作为锁 key，`SET NX EX` 加 5 分钟过期
- 座位状态存在 Redis Hash（`livetix:session:seats:{sessionId}`）里，锁住即改状态
- 单笔订单最多选 6 个座位
- 选座超时自动释放，不会因为用户关掉页面就永久占座

这样张三选 A1、李四选 B2 可以完全并行，只有真正抢同一个座位时才会串行。锁粒度下沉到单个座位之后，并发度比整场锁高了一个量级。

### 3. 下单削峰：RocketMQ 异步下单 + 结果轮询

秒杀请求进来后，Redis 预扣库存成功就直接返回"排队中"，真正的订单落库交给 MQ 异步做：

- 生产者发到 `livetix-order-topic`（tag: `order-create`）
- 消费者拿到消息后调用 `orderService.createOrderAsync` 落库
- 处理结果写回 Redis（`requestId` 为键，TTL 5 分钟），前端拿 `requestId` 轮询 `/orders/create-status` 拿最终结果

这样瞬时流量只打 Redis（扛得住），数据库按自己的节奏消费消息，不会被瞬间打满。

**异常回滚**这块单独处理了：如果消费过程中抛异常，DB 事务会自动回滚，但 Redis 的预扣不会——所以消费者里显式做了 `rollbackRedisStock`，把预扣的库存加回去，并释放用户的防重锁，允许立即重试。

### 4. 超时关单：延迟消息 + 定时任务双保险

下单后 15 分钟未支付，需要自动关单并回补库存。这里用了两条路径：

- **主路径**：下单时往 `livetix-delay-topic` 发一条延迟消息，到点触发 `cancelIfStillPending`
- **兜底**：`OrderTimeoutScheduler` 每 60 秒扫一次库，把超时未支付的订单捞出来处理

为什么两条都要：延迟消息可能因为 Broker 异常、消息堆积等原因漏掉，定时任务扫库能保证"不漏"；而延迟消息能保证"及时"，不用等到下一次扫描。关单和回补都按订单号做幂等，消费重试不会导致重复回补。

### 5. 接口限流：Redis 令牌桶

用 Lua 实现了一个原子令牌桶（`token_bucket.lua`），按容量 + 填充速率控制放行。脚本里同时维护"剩余令牌数"和"上次填充时间"两个 key，读、算、写在一个原子操作里完成，避免并发下令牌被超发。

### 6. 可观测性

- **`TraceIdFilter`**：每个请求生成/透传 TraceId，写进 MDC，日志里能带上同一个链路标识
- **`OperationLogAspect`**：AOP 切面记录后台管理的关键操作（谁、什么时候、改了什么），落到操作日志表
- **`GlobalExceptionHandler`**：统一异常出口，业务异常和系统异常分开处理
- **Spring Boot Actuator**：健康检查

## 功能模块

**用户侧**：演出浏览与筛选、场次选择、可视化选座、下单支付、订单管理、退款申请、余额钱包、实名认证、演出收藏、开演提醒、站内通知

**管理侧**：演出管理、场次与座位管理、订单管理、退款审核、财务统计、Banner 轮播、分类与场馆管理、用户管理、角色权限管理（角色-权限 RBAC）、系统配置、操作日志

## 项目结构

```
LiveTix/
├── backend/
│   └── src/main/
│       ├── java/com/livetix/
│       │   ├── controller/          # 接口层（user / admin 分开）
│       │   ├── service/             # 业务层：库存、座位锁、订单、支付、退款、钱包
│       │   ├── mq/                  # RocketMQ 生产者、消费者、超时调度
│       │   ├── mapper/              # MyBatis-Plus Mapper
│       │   ├── entity/ dto/ vo/     # 数据模型
│       │   ├── config/              # Sa-Token、Redis、AOP、TraceId、Swagger
│       │   └── common/              # 统一返回、异常、常量、工具
│       └── resources/
│           ├── scripts/             # Lua 脚本：库存扣减 / 回补 / 令牌桶
│           ├── db/init.sql          # 建表 + 初始数据
│           └── application.yml
├── frontend/                        # Vue 3 前台 + 管理后台
├── screenshots/
└── docker-compose.yml               # MySQL + Redis + Backend + Frontend
```

## 压测结果

JMeter 500 并发抢同一场次：

| 指标 | 结果 |
|------|------|
| 超卖次数 | 0 |
| 响应时间 | 稳定在 50ms 以内 |
| 座位重复锁定 | 0 |

优化前的版本（`synchronized` 实现）在 200 并发下就会超卖，这部分代码保留在另一个仓库里做对照。

## 踩过的坑

1. **`synchronized` 锁字符串常量** —— 本来想用 `str.intern()` 保证锁对象唯一，结果字符串常量池是 JVM 全局共享的，不同业务会互相阻塞，而且常量池的对象很难被 GC。后来改成用 `ConcurrentHashMap` 管理锁对象。
2. **库存回补用 `INCRBY`** —— 没预热的演出被凭空建出库存键，读到脏库存。改成 Lua 里先判断 key 存在再回补。
3. **只靠延迟消息关单** —— MQ 出问题时会有漏网的订单，库存一直占着。加了定时任务扫库兜底。
4. **消费端没做幂等** —— MQ 重试导致重复处理。改成按订单号幂等 + Redis 防重锁。
5. **异常时忘了回滚 Redis 预扣** —— DB 事务回滚了但 Redis 没回滚，库存凭空少掉。在消费者里显式补了回滚逻辑。

## 后续可以做的

- [ ] 接入 Sentinel，把限流熔断做得更体系化
- [ ] 本地缓存（Caffeine）扛一层热点数据，减轻 Redis 压力
- [ ] WebSocket 推送下单结果，替代前端轮询
- [ ] 订单量上来之后做分库分表
- [ ] 补充单元测试和集成测试

## License

MIT