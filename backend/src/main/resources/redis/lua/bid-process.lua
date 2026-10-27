-- KEYS[1-4,7]은 경매별 상태이고 KEYS[5-6]은 회원 전역 지갑 Hash다.
local memberId = ARGV[1]
local amount = tonumber(ARGV[2])
local requestId = ARGV[3]
local requestedAtEpochMillis = tonumber(ARGV[4])
local requestedAt = ARGV[5]

if redis.call('EXISTS', KEYS[1]) == 0 then
    return 'ERR|AUCTION_NOT_FOUND'
end

local auctionStatus = redis.call('HGET', KEYS[1], 'status')
if auctionStatus ~= 'LIVE' then
    return 'ERR|AUCTION_NOT_LIVE'
end

local endAtEpochMillis = tonumber(redis.call('HGET', KEYS[1], 'endAt'))
if requestedAtEpochMillis >= endAtEpochMillis then
    return 'ERR|AUCTION_ALREADY_ENDED'
end

if redis.call('HEXISTS', KEYS[7], requestId) == 1 then
    return 'ERR|DUPLICATE_REQUEST'
end

local reservePrice = tonumber(redis.call('HGET', KEYS[1], 'reservePrice'))
if amount < reservePrice then
    return 'ERR|BID_BELOW_RESERVE_PRICE'
end

local previousBidValue = redis.call('HGET', KEYS[2], memberId)
local previousBidAmount = tonumber(previousBidValue) or 0
if previousBidValue and amount <= previousBidAmount then
    return 'ERR|BID_NOT_HIGHER_THAN_PREVIOUS'
end

local sellerId = redis.call('HGET', KEYS[1], 'sellerId')
if sellerId == memberId then
    return 'ERR|SELLER_CANNOT_BID'
end

local participation = redis.call('HGET', KEYS[4], memberId)
if participation == 'PREDICTION' then
    return 'ERR|PREDICTION_ALREADY_JOINED'
end

local additionalHoldAmount = amount - previousBidAmount
local availableBalance = tonumber(redis.call('HGET', KEYS[5], memberId)) or 0
if availableBalance < additionalHoldAmount then
    return 'ERR|INSUFFICIENT_AVAILABLE_BALANCE'
end

redis.call('HSET', KEYS[2], memberId, amount)
redis.call('HSET', KEYS[3], memberId, requestedAt)

local bidCount = redis.call('HINCRBY', KEYS[1], 'bidCount', 1)
local participantCount = tonumber(redis.call('HGET', KEYS[1], 'participantCount')) or 0
if participation ~= 'BID' then
    redis.call('HSET', KEYS[4], memberId, 'BID')
    participantCount = redis.call('HINCRBY', KEYS[1], 'participantCount', 1)
end

availableBalance = redis.call('HINCRBY', KEYS[5], memberId, -additionalHoldAmount)
local totalHeldAmount = redis.call('HINCRBY', KEYS[6], memberId, additionalHoldAmount)
redis.call('HINCRBY', KEYS[1], 'version', 1)
redis.call('HSET', KEYS[1], 'updatedAt', requestedAt)
redis.call('HSET', KEYS[7], requestId, requestedAt)

return table.concat({
    'OK',
    previousBidAmount,
    amount,
    additionalHoldAmount,
    totalHeldAmount,
    availableBalance,
    requestedAt,
    bidCount,
    participantCount
}, '|')
