package com.ssafy.bidlive.auction.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.auction.application.UpcomingAuctionItem;
import com.ssafy.bidlive.auction.domain.AuctionStatus;

/** 곧 시작할 경매방 카드에 필요한 공개 정보를 반환합니다. */
public record UpcomingAuctionItemResponse(
	long auctionId,
	String productTitle,
	String imageUrl,
	OffsetDateTime scheduledAt,
	long waitingCount,
	AuctionStatus status
) {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public static UpcomingAuctionItemResponse from(UpcomingAuctionItem item) {
		return new UpcomingAuctionItemResponse(
			item.auctionId(),
			item.productTitle(),
			item.imageUrl(),
			atKorea(item.scheduledAt()),
			item.waitingCount(),
			item.status()
		);
	}

	private static OffsetDateTime atKorea(Instant instant) {
		return instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}
}
