package com.ssafy.bidlive.auction.application;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.auction.domain.CatchUpSummarySourceType;

/** AI-03 조회 응답을 기술 독립적으로 표현한다. */
public record CatchUpSummaryResult(
	long auctionId,
	Instant generatedAt,
	Instant expiresAt,
	long bidCount,
	long participantCount,
	int extensionCount,
	int extendedMinutes,
	List<String> sellerMentions,
	Instant serverTime,
	Instant endAt,
	CatchUpSummarySourceType sourceType
) {
	public CatchUpSummaryResult {
		sellerMentions = sellerMentions == null ? List.of() : List.copyOf(sellerMentions);
	}
}
