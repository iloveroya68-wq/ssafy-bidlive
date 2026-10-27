package com.ssafy.bidlive.auction.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.ssafy.bidlive.auction.application.CatchUpSummaryResult;
import com.ssafy.bidlive.auction.domain.CatchUpSummarySourceType;

/** AI-03 공개 응답이며 개별 입찰 금액이나 회원 식별값을 포함하지 않는다. */
public record CatchUpSummaryResponse(
	long auctionId,
	OffsetDateTime generatedAt,
	OffsetDateTime expiresAt,
	BidSummary bidSummary,
	ExtensionSummary extensionSummary,
	List<String> sellerMentions,
	OffsetDateTime serverTime,
	OffsetDateTime endAt,
	CatchUpSummarySourceType sourceType
) {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public record BidSummary(long bidCount, long participantCount) {
	}

	public record ExtensionSummary(int extensionCount, int extendedMinutes) {
	}

	public static CatchUpSummaryResponse from(CatchUpSummaryResult result) {
		return new CatchUpSummaryResponse(
			result.auctionId(),
			atKorea(result.generatedAt()),
			atKorea(result.expiresAt()),
			new BidSummary(result.bidCount(), result.participantCount()),
			new ExtensionSummary(result.extensionCount(), result.extendedMinutes()),
			result.sellerMentions(),
			atKorea(result.serverTime()),
			atKorea(result.endAt()),
			result.sourceType()
		);
	}

	private static OffsetDateTime atKorea(Instant value) {
		return value == null ? null : value.atZone(KOREA_ZONE).toOffsetDateTime();
	}
}
