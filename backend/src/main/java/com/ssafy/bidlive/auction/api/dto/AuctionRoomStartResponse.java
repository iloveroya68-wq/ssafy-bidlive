package com.ssafy.bidlive.auction.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.auction.application.AuctionRoomStartResult;
import com.ssafy.bidlive.auction.domain.AuctionStatus;

/** 경매 방송 시작 응답이다. `endAt`은 서버가 계산한 값이고 `serverTime`이 그 기준 시각이다. */
public record AuctionRoomStartResponse(
	Long auctionId,
	AuctionStatus status,
	OffsetDateTime startedAt,
	OffsetDateTime endAt,
	int durationMinutes,
	OffsetDateTime serverTime
) {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public static AuctionRoomStartResponse from(AuctionRoomStartResult result) {
		return new AuctionRoomStartResponse(
			result.auctionId(),
			result.status(),
			atKorea(result.startedAt()),
			atKorea(result.endAt()),
			result.durationMinutes(),
			atKorea(result.serverTime())
		);
	}

	private static OffsetDateTime atKorea(Instant instant) {
		return instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}
}
