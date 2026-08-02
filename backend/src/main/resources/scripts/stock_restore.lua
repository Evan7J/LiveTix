-- 回补 Redis 预热库存（订单取消/超时/退款恢复 DB 库存后调用）
-- KEYS[1]: 库存缓存 Key (livetix:stock:pre:{showId})
-- ARGV[1]: 回补数量
-- Returns: 回补后的库存; -1 表示该演出未预热（键不存在），无需回补
--
-- 说明: 用 Lua 保证"存在才回补"的原子性 —— 若直接 INCRBY,
-- 未预热的演出会被误建键，导致下次秒杀读到脏库存

local current = tonumber(redis.call('GET', KEYS[1]))
if current == nil then
    return -1
end

local restored = current + tonumber(ARGV[1])
-- 与 stock_deduct.lua 一致: 每次写入后刷新 24h TTL
redis.call('SET', KEYS[1], restored, 'EX', 86400)
return restored
