local stateKey = KEYS[1]
local requesterId = ARGV[1]
local requestedAtEpochMillis = tonumber(ARGV[2])

if redis.call('EXISTS', stateKey) == 0 then
    return 'ERR|AUCTION_NOT_FOUND'
end

local values = redis.call(
    'HMGET',
    stateKey,
    'sellerId',
    'status',
    'extensionCount',
    'endAt',
    'version'
)
local sellerId = values[1]
local status = values[2]
local sellerIdNumber = tonumber(sellerId)
local extensionCount = tonumber(values[3])
local previousEndAt = tonumber(values[4])
local version = tonumber(values[5])

if sellerId == false
    or status == false
    or sellerIdNumber == nil
    or sellerIdNumber <= 0
    or extensionCount == nil
    or extensionCount < 0
    or extensionCount > 3
    or previousEndAt == nil
    or previousEndAt <= 0
    or version == nil
    or version < 0
    or requestedAtEpochMillis == nil
    or requestedAtEpochMillis <= 0 then
    return 'SYS|INVALID_AUCTION_STATE'
end

if sellerId ~= requesterId then
    return 'ERR|NOT_AUCTION_SELLER'
end
if status ~= 'LIVE' then
    return 'ERR|AUCTION_NOT_LIVE'
end
if extensionCount >= 3 then
    return 'ERR|EXTENSION_LIMIT_EXCEEDED'
end
if previousEndAt - requestedAtEpochMillis < 60000 then
    return 'ERR|EXTENSION_TOO_LATE'
end

local endAt = previousEndAt + 300000
local nextExtensionCount = extensionCount + 1
local remainingExtensions = 3 - nextExtensionCount
local nextVersion = version + 1

redis.call(
    'HSET',
    stateKey,
    'endAt', string.format('%.0f', endAt),
    'extensionCount', tostring(nextExtensionCount),
    'version', string.format('%.0f', nextVersion)
)

return table.concat({
    'OK',
    string.format('%.0f', previousEndAt),
    string.format('%.0f', endAt),
    tostring(nextExtensionCount),
    tostring(remainingExtensions),
    string.format('%.0f', nextVersion)
}, '|')
