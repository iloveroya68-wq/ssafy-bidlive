package com.ssafy.bidlive.wallet.application;

import com.ssafy.bidlive.wallet.domain.BettingPointSettlementType;

/** 예측 도메인이 계산한 배당 또는 환불을 베팅 지갑에 반영하도록 요청한다. */
public record BettingPointSettlementCommand(
	Long memberId,
	Long predictionId,
	Long predictionResultId,
	BettingPointSettlementType settlementType,
	long amount,
	String idempotencyKey
) {

	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

	public BettingPointSettlementCommand {
		requirePositiveId(memberId, "memberId");
		requirePositiveId(predictionId, "predictionId");
		requirePositiveId(predictionResultId, "predictionResultId");
		if (settlementType == null) {
			throw new IllegalArgumentException("settlementType must not be null");
		}
		if (amount <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new IllegalArgumentException("idempotencyKey must not be blank");
		}
		if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new IllegalArgumentException(
				"idempotencyKey must not exceed "
					+ MAX_IDEMPOTENCY_KEY_LENGTH + " characters"
			);
		}
	}

	private static void requirePositiveId(Long value, String name) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}
}
