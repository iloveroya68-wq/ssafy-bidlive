package com.ssafy.bidlive.auction.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.auction.application.AuctionExtensionResult;

/** 판매자 연장 전후의 마감 시각과 남은 연장 가능 횟수를 표현한다. */
public record AuctionExtensionResponse(
	Long auctionId,
	OffsetDateTime previousEndAt,
	OffsetDateTime endAt,
	int extensionCount,
	int remainingExtensions
) {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public static AuctionExtensionResponse from(AuctionExtensionResult result) {
		return new AuctionExtensionResponse(
			result.auctionId(),
			atKorea(result.previousEndAt()),
			atKorea(result.endAt()),
			result.extensionCount(),
			result.remainingExtensions()
		);
	}

	private static OffsetDateTime atKorea(Instant instant) {
		return instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}
}
