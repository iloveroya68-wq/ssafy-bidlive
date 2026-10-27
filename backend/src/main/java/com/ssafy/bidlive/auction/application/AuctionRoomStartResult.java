package com.ssafy.bidlive.auction.application;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionStatus;

/**
 * 방송 시작 결과다. {@code endAt}은 서버가 `startedAt + durationMinutes`로 계산한 값이고,
 * 프론트 타이머(BID-05)가 {@code serverTime}과 함께 이 값을 기준으로 남은 시간을 표시한다.
 */
public record AuctionRoomStartResult(
	Long auctionId,
	AuctionStatus status,
	Instant startedAt,
	Instant endAt,
	int durationMinutes,
	Instant serverTime
) {
}
