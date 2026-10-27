package com.ssafy.bidlive.prediction.application;

import java.math.BigDecimal;

/** 일일 예측 리더보드 스냅샷에 저장되는 회원별 순위 항목이다. */
public record PredictionLeaderboardEntry(
	long rank,
	long memberId,
	String nickname,
	int settledPredictionCount,
	long totalStakedPoints,
	long totalPayoutPoints,
	BigDecimal payoutRate
) {
}
