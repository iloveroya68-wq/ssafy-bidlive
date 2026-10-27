package com.ssafy.bidlive.wallet.application;

/** 예측 참여에 사용할 베팅 포인트 차감과 원장 기록을 요청한다. */
public record BettingPointBetCommand(
	Long memberId,
	Long predictionId,
	long amount,
	String idempotencyKey
) {

	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

	public BettingPointBetCommand {
		requirePositiveId(memberId, "memberId");
		requirePositiveId(predictionId, "predictionId");
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
