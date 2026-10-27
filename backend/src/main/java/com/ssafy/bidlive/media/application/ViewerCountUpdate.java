package com.ssafy.bidlive.media.application;

import java.time.OffsetDateTime;
import java.util.Objects;

/** 경매별 활성 STOMP 세션 수와 측정 시각을 전달하는 애플리케이션 모델입니다. */
public record ViewerCountUpdate(
	long auctionId,
	long viewerCount,
	OffsetDateTime measuredAt
) {

	public ViewerCountUpdate {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId는 양수여야 합니다.");
		}
		if (viewerCount < 0) {
			throw new IllegalArgumentException("viewerCount는 음수일 수 없습니다.");
		}
		Objects.requireNonNull(measuredAt, "measuredAt");
	}
}
