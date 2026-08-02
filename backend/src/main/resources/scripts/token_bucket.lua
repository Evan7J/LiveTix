-- Atomic Token Bucket Lua Script
-- KEYS[1]: bucket tokens count key
-- KEYS[2]: last fill timestamp key
-- ARGV[1]: bucket capacity (max tokens)
-- ARGV[2]: token fill rate per second
-- ARGV[3]: current unix timestamp in seconds
-- Returns: 1 if token acquired, 0 if rate limited

local tokens = tonumber(redis.call('GET', KEYS[1]))
if tokens == nil then
    tokens = tonumber(ARGV[1])
end

local last_fill = tonumber(redis.call('GET', KEYS[2]))
if last_fill == nil then
    last_fill = tonumber(ARGV[3])
end

local now = tonumber(ARGV[3])
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])

-- Calculate tokens to add based on elapsed time
local elapsed = math.max(0, now - last_fill)
local new_tokens = math.floor(elapsed * rate)
tokens = math.min(capacity, tokens + new_tokens)
last_fill = now

-- Try to consume one token
if tokens > 0 then
    tokens = tokens - 1
    redis.call('SET', KEYS[1], tokens, 'EX', 60)
    redis.call('SET', KEYS[2], last_fill, 'EX', 60)
    return 1
end

-- No token available: update counters but return 0
redis.call('SET', KEYS[1], tokens, 'EX', 60)
redis.call('SET', KEYS[2], last_fill, 'EX', 60)
return 0
