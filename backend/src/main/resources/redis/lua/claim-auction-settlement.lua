local stateKey = KEYS[1]
local requestedAtEpochMillis = tonumber(ARGV[1])
local requestedAt = ARGV[2]

if requestedAtEpochMillis == nil or requestedAtEpochMillis <= 0
    or requestedAt == nil or requestedAt == '' then
    return 'SYS|INVALID_SETTLEMENT_CLAIM_ARGUMENTS'
end

if redis.call('EXISTS', stateKey) == 0 then
    return 'ERR|AUCTION_NOT_FOUND'
end

local status = redis.call('HGET', stateKey, 'status')
if status == false then
    return 'SYS|INVALID_AUCTION_STATE'
end
if status == 'SETTLING' then
    return 'ERR|ALREADY_SETTLING'
end
if status == 'ENDED' then
    return 'ERR|ALREADY_ENDED'
end
if status == 'WAITING' then
    return 'ERR|AUCTION_NOT_LIVE'
end
if status ~= 'LIVE' then
    return 'SYS|INVALID_AUCTION_STATE'
end

local values = redis.call('HMGET', stateKey, 'endAt', 'version')
local endAtEpochMillis = tonumber(values[1])
local version = tonumber(values[2])
if endAtEpochMillis == nil
    or endAtEpochMillis <= 0
    or version == nil
    or version < 0 then
    return 'SYS|INVALID_AUCTION_STATE'
end

if requestedAtEpochMillis < endAtEpochMillis then
    return 'ERR|AUCTION_NOT_DUE'
end

local nextVersion = version + 1
redis.call(
    'HSET',
    stateKey,
    'status', 'SETTLING',
    'version', string.format('%.0f', nextVersion),
    'updatedAt', requestedAt
)

return 'OK|CLAIMED'
