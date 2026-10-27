package com.ssafy.bidlive.media.domain;

import java.time.OffsetDateTime;

/** 낙찰 시점 방송 스냅샷의 저장 결과를 방·경매·이미지 URL·캡처 시각으로 표현합니다. */
public record LiveSnapshot(
	String roomId,
	Long auctionId,
	String imageUrl,
	OffsetDateTime capturedAt
) {

	public LiveSnapshot {
		if (roomId == null || roomId.isBlank()) {
			throw new IllegalArgumentException("Snapshot room id must not be blank");
		}
		if (auctionId == null || auctionId <= 0) {
			throw new IllegalArgumentException("Snapshot auctionId must be positive");
		}
		if (imageUrl == null || imageUrl.isBlank()) {
			throw new IllegalArgumentException("Snapshot image url must not be blank");
		}
		if (capturedAt == null) {
			throw new IllegalArgumentException("Snapshot capturedAt must not be null");
		}
	}
}
