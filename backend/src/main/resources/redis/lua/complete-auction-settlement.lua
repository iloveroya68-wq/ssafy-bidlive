local key = KEYS[1]
local completedAt = ARGV[1]

if redis.call('EXISTS', key) == 0 then
    return 'ERR|AUCTION_NOT_FOUND'
end

local status = redis.call('HGET', key, 'status')
if status == 'ENDED' then
    return 'OK|ALREADY_ENDED'
end
if status ~= 'SETTLING' then
    return 'ERR|AUCTION_NOT_SETTLING'
end

local version = tonumber(redis.call('HGET', key, 'version') or '0') + 1
redis.call(
    'HSET',
    key,
    'status', 'ENDED',
    'version', tostring(version),
    'updatedAt', completedAt
)
return 'OK|COMPLETED'
