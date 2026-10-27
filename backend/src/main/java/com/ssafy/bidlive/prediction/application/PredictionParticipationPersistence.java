package com.ssafy.bidlive.prediction.application;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 예측 참여의 멱등 잠금과 PostgreSQL 저장에 필요한 영속성 Port다. */
public interface PredictionParticipationPersistence {

	void lockIdempotencyKey(String idempotencyKey);

	Optional<StoredPrediction> findByIdempotencyKey(String idempotencyKey);

	Optional<StoredPrediction> findByAuctionIdAndMemberId(
		Long auctionId,
		Long memberId
	);

	StoredPrediction save(NewPrediction prediction);

	record NewPrediction(
		Long auctionId,
		Long memberId,
		Long predictedPrice,
		Long stakeAmount,
		String idempotencyKey,
		OffsetDateTime submittedAt
	) {
	}

	record StoredPrediction(
		Long predictionId,
		Long auctionId,
		Long memberId,
		Long predictedPrice,
		Long stakeAmount,
		String idempotencyKey,
		OffsetDateTime submittedAt
	) {
	}
}
