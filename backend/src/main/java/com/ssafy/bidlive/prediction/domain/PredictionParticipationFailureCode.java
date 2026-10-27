package com.ssafy.bidlive.prediction.domain;

/** PRED-01 Redis 참여 처리에서 계약상 구분하는 거부 사유다. */
public enum PredictionParticipationFailureCode {
	AUCTION_NOT_FOUND,
	AUCTION_NOT_LIVE,
	PREDICTION_CLOSED,
	SELLER_CANNOT_PREDICT,
	BIDDER_CANNOT_PREDICT,
	PREDICTION_ALREADY_EXISTS,
	INSUFFICIENT_BETTING_POINTS,
	IDEMPOTENCY_KEY_REUSED
}
