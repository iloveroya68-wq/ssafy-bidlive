package com.ssafy.bidlive.auction.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.auction.application.AuctionRoomSummaryItem;
import com.ssafy.bidlive.auction.domain.AuctionStatus;

/** 목록 카드 한 건의 응답이다. */
public record AuctionRoomSummaryResponse(
	Long auctionId,
	String title,
	String thumbnailUrl,
	String categoryCode,
	String sellerNickname,
	long reservePrice,
	long bidCount,
	long bidParticipantCount,
	long predictionParticipantCount,
	OffsetDateTime scheduledAt,
	OffsetDateTime endAt,
	Long soldPrice,
	OffsetDateTime endedAt,
	AuctionStatus status,
	int viewerCount
) {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public static AuctionRoomSummaryResponse from(AuctionRoomSummaryItem item) {
		return new AuctionRoomSummaryResponse(
			item.auctionId(),
			item.title(),
			item.thumbnailUrl(),
			item.categoryCode(),
			item.sellerNickname(),
			item.reservePrice(),
			item.bidCount(),
			item.bidParticipantCount(),
			item.predictionParticipantCount(),
			atKorea(item.scheduledAt()),
			atKorea(item.endAt()),
			item.soldPrice(),
			atKorea(item.endedAt()),
			item.status(),
			item.viewerCount()
		);
	}

	static OffsetDateTime atKorea(Instant instant) {
		return instant == null ? null : instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}
}
