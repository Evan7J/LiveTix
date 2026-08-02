# LiveTix — 演唱会票务秒杀系统

一个基于 Spring Boot 的演唱会票务系统，核心是高并发抢票场景下的库存扣减方案。

练手项目，用来消化 Java 后端常用技术栈，顺便放到简历上找实习。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2 |
| ORM | MyBatis-Plus |
| 缓存 | Redis 7 |
| 消息队列 | RocketMQ 5.3 |
| 认证授权 | Sa-Token（RBAC） |
| 数据库 | MySQL 8.0 |
| 前端 | Vue 3 + Vite + Element Plus + Pinia |
| 部署 | Docker Compose |

## 核心链路：抢票下单

抢票时最怕的就是超卖，这里的处理思路是三层防护：

1. **Redis Lua 预扣** — 用户点击下单时，先走 Lua 脚本在 Redis 里原子扣减库存，扣成功了才继续。这样 99% 的无效请求在 Redis 层就被拦截了，不会打到数据库。
2. **RocketMQ 异步落库** — 扣减成功后发消息给 MQ，消费者异步地把订单写入 MySQL。同步改异步，接口响应快很多。
3. **DB 乐观锁兜底** — `update show set available_stock = available_stock - ? where id = ? and available_stock >= ?`，即使前面两层都漏了，数据库也能拦住。

另外还做了令牌桶限流、请求幂等、用户维度的防重复提交。

## 功能模块

**用户端**
- 注册登录（手机号验证码）
- 演出浏览、搜索、分类筛选
- 选座购票（SVG 座位图）
- 下单支付（钱包余额支付）
- 订单管理、退款申请
- 收藏、开售提醒、实名认证

**管理后台**
- 演出管理（创建场次、设置票档、编辑座位图）
- 订单管理、退款审核
- 财务管理、数据统计
- 用户管理、角色权限
- Banner 管理、系统配置

## 快速启动

需要先装好 Docker。

```bash
# 克隆项目
git clone https://github.com/Evan7J/livetix.git
cd livetix

# 一键启动所有服务（MySQL + Redis + RocketMQ + 后端 + 前端）
docker compose up -d

# 导入数据库初始化脚本
# 等待 MySQL 启动后，手动执行 backend/src/main/resources/db/init.sql

# 访问
# 前端：http://localhost:3000
# 后端接口：http://localhost:8080
# 管理后台：http://localhost:3000/admin
```

## 项目结构

```
backend/
├── common/        # 统一返回、异常处理、Redis Key 常量
├── config/        # 跨域、Redis、Sa-Token、TraceId 等配置
├── controller/    # 接口层（admin/ 和 user/ 分开）
├── dto/           # 数据传输对象
├── entity/        # 数据库实体
├── mapper/        # MyBatis-Plus Mapper
├── mq/            # RocketMQ 生产者和消费者
├── service/       # 业务逻辑层
└── resources/
    ├── db/        # 数据库迁移脚本
    └── scripts/   # Redis Lua 脚本（库存扣减/恢复/令牌桶）

frontend/
├── src/
│   ├── api/       # 接口封装
│   ├── views/     # 页面组件（admin/ 和业务页面）
│   ├── components/# 公共组件（座位选择器、座位图编辑器）
│   ├── stores/    # Pinia 状态管理
│   └── utils/     # 请求封装、XSS 清洗
```

## 待完善

- [ ] 接入支付宝/微信真实支付
- [ ] 压力测试报告（JMeter）
- [ ] 前台展示优化（列表页首屏加载略慢）
- [ ] 部分查询接口加缓存
- [ ] 日志接入 ELK

## 免责声明

这个项目是学习用途，代码里写了大量的注释和面试要点，部分功能实现得比较粗糙（比如支付就是简单的钱包扣款），主要目的是 **理解高并发场景下的技术方案**，不适合直接用在生产环境。