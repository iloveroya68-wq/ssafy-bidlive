package com.ssafy.bidlive.bid.application;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 승인된 입찰의 멱등 잠금, 참여자 직렬화와 PostgreSQL 이력 저장 계약이다. */
public interface BidPersistence {

	void lockIdempotencyKey(String idempotencyKey);

	void lockParticipation(Long auctionId, Long memberId);

	Optional<StoredBid> findByIdempotencyKey(String idempotencyKey);

	boolean hasPredictionParticipation(Long auctionId, Long memberId);

	StoredBid save(NewBid bid);

	record NewBid(
		Long auctionId,
		Long memberId,
		Long amount,
		String idempotencyKey,
		OffsetDateTime submittedAt
	) {
	}

	record StoredBid(
		Long bidId,
		Long auctionId,
		Long memberId,
		Long amount,
		Long previousBidAmount,
		Integer sequenceNo,
		String idempotencyKey,
		OffsetDateTime submittedAt
	) {
		public long additionalHoldAmount() {
			return amount - previousBidAmount;
		}
	}
}
