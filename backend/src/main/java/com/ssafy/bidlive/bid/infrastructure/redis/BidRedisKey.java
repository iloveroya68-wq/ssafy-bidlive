package com.ssafy.bidlive.bid.infrastructure.redis;

import java.util.List;

import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;

/** 경매 상태와 회원 전역 지갑 Key를 한 해시 슬롯에 배치한다. */
final class BidRedisKey {

	private BidRedisKey() {
	}

	static List<String> processingKeys(long auctionId) {
		return RealtimeAuctionRedisKey.bidProcessingKeys(auctionId);
	}
}
