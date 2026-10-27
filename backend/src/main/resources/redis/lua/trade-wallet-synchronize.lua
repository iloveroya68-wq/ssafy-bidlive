local memberId = ARGV[1]
local ledgerId = ARGV[2]
local availableDelta = ARGV[3]
local heldDelta = ARGV[4]
local availableBalanceAfter = ARGV[5]
local heldBalanceAfter = ARGV[6]
local processedLedgerTtlSeconds = ARGV[7]

local function lessThanOrEqual(left, right)
    if string.len(left) ~= string.len(right) then
        return string.len(left) < string.len(right)
    end
    return left <= right
end

local availableExists = redis.call('HEXISTS', KEYS[1], memberId)
local heldExists = redis.call('HEXISTS', KEYS[2], memberId)

if availableExists ~= heldExists then
    return -1
end

if availableExists == 0 then
    redis.call('HSET', KEYS[1], memberId, availableBalanceAfter)
    redis.call('HSET', KEYS[2], memberId, heldBalanceAfter)
    redis.call('HSET', KEYS[3], memberId, ledgerId)
    return 2
end

local watermark = redis.call('HGET', KEYS[3], memberId)
if watermark and lessThanOrEqual(ledgerId, watermark) then
    return 0
end

if redis.call('EXISTS', KEYS[4]) == 1 then
    return 0
end

local currentAvailable = redis.call('HGET', KEYS[1], memberId)
local currentHeld = redis.call('HGET', KEYS[2], memberId)
if currentAvailable == availableBalanceAfter
    and currentHeld == heldBalanceAfter then
    redis.call(
        'SET', KEYS[4], memberId, 'EX', processedLedgerTtlSeconds
    )
    return 0
end

local updatedAvailable = tonumber(currentAvailable)
    + tonumber(availableDelta)
local updatedHeld = tonumber(currentHeld) + tonumber(heldDelta)

if updatedAvailable < 0 or updatedHeld < 0 then
    return -2
end

redis.call('HINCRBY', KEYS[1], memberId, availableDelta)
redis.call('HINCRBY', KEYS[2], memberId, heldDelta)
redis.call('SET', KEYS[4], memberId, 'EX', processedLedgerTtlSeconds)
return 1
