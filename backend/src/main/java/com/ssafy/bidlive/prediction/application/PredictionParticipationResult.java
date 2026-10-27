package com.ssafy.bidlive.prediction.application;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode;

/** 예측 참여 원자 처리의 승인 결과 또는 명세상 실패 코드를 반환한다. */
public record PredictionParticipationResult(
	boolean accepted,
	OffsetDateTime submittedAt,
	Long participantCount,
	Long availableBalance,
	boolean duplicated,
	PredictionParticipationFailureCode failureCode
) {

	public PredictionParticipationResult {
		if (accepted && failureCode != null) {
			throw new IllegalArgumentException(
				"accepted result must not have a failure code"
			);
		}
		if (!accepted && failureCode == null) {
			throw new IllegalArgumentException(
				"rejected result must have a failure code"
			);
		}
	}

	public static PredictionParticipationResult accepted(
		OffsetDateTime submittedAt,
		long participantCount,
		long availableBalance,
		boolean duplicated
	) {
		return new PredictionParticipationResult(
			true,
			submittedAt,
			participantCount,
			availableBalance,
			duplicated,
			null
		);
	}

	public static PredictionParticipationResult rejected(
		PredictionParticipationFailureCode failureCode
	) {
		return new PredictionParticipationResult(
			false,
			null,
			null,
			null,
			false,
			failureCode
		);
	}
}
