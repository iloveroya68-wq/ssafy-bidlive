package com.ssafy.bidlive.prediction.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionLeaderboardEntry;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardQueryResult;

/** PRED-05 일일 예측 리더보드와 로그인 회원의 전체 순위를 반환한다. */
public record PredictionLeaderboardResponse(
	LocalDate rankingDate,
	OffsetDateTime calculatedAt,
	List<RankEntry> items,
	RankEntry myRank,
	String nextCursor,
	boolean hasNext
) {

	public PredictionLeaderboardResponse {
		items = List.copyOf(items);
	}

	public static PredictionLeaderboardResponse from(
		PredictionLeaderboardQueryResult result
	) {
		return new PredictionLeaderboardResponse(
			result.rankingDate(),
			result.calculatedAt(),
			result.items().stream()
				.map(PredictionLeaderboardResponse::toRankEntry)
				.toList(),
			result.myRank() == null ? null : toRankEntry(result.myRank()),
			result.nextCursor(),
			result.hasNext()
		);
	}

	private static RankEntry toRankEntry(PredictionLeaderboardEntry entry) {
		return new RankEntry(
			entry.rank(),
			entry.nickname(),
			entry.settledPredictionCount(),
			entry.totalStakedPoints(),
			entry.totalPayoutPoints(),
			entry.payoutRate()
		);
	}

	public record RankEntry(
		Long rank,
		String nickname,
		Integer settledPredictionCount,
		Long totalStakedPoints,
		Long totalPayoutPoints,
		BigDecimal payoutRate
	) {
	}
}
