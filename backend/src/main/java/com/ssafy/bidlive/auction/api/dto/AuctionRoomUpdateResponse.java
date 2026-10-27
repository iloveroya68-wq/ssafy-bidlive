package com.ssafy.bidlive.auction.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.auction.application.AuctionRoomUpdateResult;

/** 경매방 수정 응답이다. 부분 수정이어도 확정된 전체 값을 돌려준다. */
public record AuctionRoomUpdateResponse(
	Long auctionId,
	Long productId,
	String status,
	Product product,
	Auction auction,
	OffsetDateTime updatedAt
) {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public record Product(
		String title,
		String description,
		String category,
		String imageUrl
	) {
	}

	public record Auction(
		long reservePrice,
		int durationMinutes,
		String startType,
		OffsetDateTime scheduledAt
	) {
	}

	public static AuctionRoomUpdateResponse from(AuctionRoomUpdateResult result) {
		return new AuctionRoomUpdateResponse(
			result.auctionId(),
			result.productId(),
			result.status(),
			new Product(
				result.title(),
				result.description(),
				result.categoryCode(),
				result.imageUrl()
			),
			new Auction(
				result.reservePrice(),
				result.durationMinutes(),
				result.startType().name(),
				atKorea(result.scheduledAt())
			),
			atKorea(result.updatedAt())
		);
	}

	private static OffsetDateTime atKorea(Instant instant) {
		return instant == null ? null : instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}
}
