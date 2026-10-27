local memberId = ARGV[1]
local availableExists = redis.call('HEXISTS', KEYS[1], memberId)
local heldExists = redis.call('HEXISTS', KEYS[2], memberId)

if availableExists ~= heldExists then
    return -1
end

if availableExists == 1 then
    return 0
end

if #ARGV == 1 then
    return 2
end

redis.call('HSET', KEYS[1], memberId, ARGV[2])
redis.call('HSET', KEYS[2], memberId, ARGV[3])
return 1
