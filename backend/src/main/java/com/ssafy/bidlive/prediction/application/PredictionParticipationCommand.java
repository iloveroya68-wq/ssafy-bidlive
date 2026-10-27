package com.ssafy.bidlive.prediction.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 예측 참여 원자 검증에 필요한 회원과 서버 접수 시각을 전달한다. */
public record PredictionParticipationCommand(
	Long auctionId,
	Long memberId,
	Long predictedPrice,
	Long stakeAmount,
	UUID clientRequestId,
	OffsetDateTime requestedAt
) {

	public PredictionParticipationCommand {
		if (auctionId == null || auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		if (memberId == null || memberId <= 0) {
			throw new IllegalArgumentException("memberId must be positive");
		}
		if (predictedPrice == null || predictedPrice <= 0) {
			throw new IllegalArgumentException(
				"predictedPrice must be positive"
			);
		}
		if (stakeAmount == null || stakeAmount <= 0) {
			throw new IllegalArgumentException(
				"stakeAmount must be positive"
			);
		}
		if (clientRequestId == null) {
			throw new IllegalArgumentException(
				"clientRequestId must not be null"
			);
		}
		if (requestedAt == null) {
			throw new IllegalArgumentException("requestedAt must not be null");
		}
	}
}
