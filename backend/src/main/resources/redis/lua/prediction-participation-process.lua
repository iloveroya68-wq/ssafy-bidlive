-- KEYS[1]은 경매 상태, KEYS[2]는 회원 참여, KEYS[3]은 요청 처리 결과,
-- KEYS[4]는 BETTING 가용 잔액 Hash다.
local memberId = ARGV[1]
local auctionId = ARGV[2]
local predictedPrice = ARGV[3]
local stakeAmount = ARGV[4]
local stakeAmountValue = tonumber(stakeAmount)
local requestedAtEpochMillis = tonumber(ARGV[5])
local requestedAt = ARGV[6]
local requestFingerprint = table.concat({
    memberId,
    auctionId,
    predictedPrice,
    stakeAmount
}, ":")

if redis.call("EXISTS", KEYS[3]) == 1 then
    local storedFingerprint = redis.call(
        "HGET",
        KEYS[3],
        "fingerprint"
    )
    if storedFingerprint ~= requestFingerprint then
        return "ERR|IDEMPOTENCY_KEY_REUSED"
    end

    local storedSubmittedAt = redis.call(
        "HGET",
        KEYS[3],
        "submittedAt"
    )
    local storedParticipantCount = redis.call(
        "HGET",
        KEYS[3],
        "participantCount"
    )
    local storedAvailableBalance = redis.call(
        "HGET",
        KEYS[3],
        "availableBalance"
    )
    if storedSubmittedAt == false
        or storedParticipantCount == false
        or storedAvailableBalance == false then
        return "ERR|INVALID_IDEMPOTENCY_STATE"
    end
    return table.concat({
        "OK",
        storedSubmittedAt,
        storedParticipantCount,
        storedAvailableBalance,
        "true"
    }, "|")
end

if redis.call('EXISTS', KEYS[1]) == 0 then
    return 'ERR|AUCTION_NOT_FOUND'
end

local auctionStatus = redis.call('HGET', KEYS[1], 'status')
if auctionStatus ~= 'LIVE' then
    return 'ERR|AUCTION_NOT_LIVE'
end

local endAtEpochMillis = tonumber(redis.call('HGET', KEYS[1], 'endAt'))
if endAtEpochMillis == nil then
    return 'ERR|INVALID_AUCTION_STATE'
end
if requestedAtEpochMillis >= endAtEpochMillis then
    return 'ERR|PREDICTION_CLOSED'
end

local sellerId = redis.call('HGET', KEYS[1], 'sellerId')
if sellerId == memberId then
    return 'ERR|SELLER_CANNOT_PREDICT'
end

local participation = redis.call('HGET', KEYS[2], memberId)
if participation == 'BID' then
    return 'ERR|BIDDER_CANNOT_PREDICT'
end
if participation == 'PREDICTION' then
    return 'ERR|PREDICTION_ALREADY_EXISTS'
end

local availableBalance =
    tonumber(redis.call('HGET', KEYS[4], memberId)) or 0
if availableBalance < stakeAmountValue then
    return 'ERR|INSUFFICIENT_BETTING_POINTS'
end

redis.call('HSET', KEYS[2], memberId, 'PREDICTION')
local participantCount = redis.call(
    'HINCRBY',
    KEYS[1],
    'participantCount',
    1
)
redis.call('HINCRBY', KEYS[1], 'version', 1)
redis.call('HSET', KEYS[1], 'updatedAt', requestedAt)
availableBalance = redis.call(
    'HINCRBY',
    KEYS[4],
    memberId,
    -stakeAmountValue
)
redis.call(
    'HSET',
    KEYS[3],
    'fingerprint',
    requestFingerprint,
    'submittedAt',
    requestedAt,
    'participantCount',
    participantCount,
    'availableBalance',
    availableBalance
)

return table.concat({
    'OK',
    requestedAt,
    participantCount,
    availableBalance,
    'false'
}, '|')
