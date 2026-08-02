-- P1-5: Redis 原子库存预扣 Lua 脚本
-- KEYS[1]: 库存缓存 Key (livetix:stock:pre:{showId})
-- ARGV[1]: 扣减数量
-- ARGV[2]: 最大库存（从 DB 加载的 initial stock）
-- Returns: 剩余库存（>=0 表示成功）, -1 表示库存不足

local stockKey = KEYS[1]
local quantity = tonumber(ARGV[1])

-- 获取当前 Redis 中的库存
local current = tonumber(redis.call('GET', stockKey))
if current == nil then
    -- 首次访问，从 ARGV[2] 初始化（管理员预热时设置）
    current = tonumber(ARGV[2])
    if current == nil or current <= 0 then
        return -1
    end
    redis.call('SET', stockKey, current, 'EX', 86400)
end

-- 检查库存是否充足
if current < quantity then
    return -1  -- 库存不足
end

-- S2: 原子扣减 + 自动续期 TTL 为 24h（每次预扣后刷新过期时间）
local remaining = current - quantity
redis.call('SET', stockKey, remaining, 'EX', 86400)
return remaining
