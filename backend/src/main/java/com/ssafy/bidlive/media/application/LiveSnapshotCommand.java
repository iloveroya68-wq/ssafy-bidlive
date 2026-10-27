package com.ssafy.bidlive.media.application;

import com.ssafy.bidlive.media.domain.LiveSnapshotImage;

/**
 * 스냅샷 저장 요청의 입력이며, 이미지와 경매의 연결은 요청 값이 아니라 서버 검증으로 확정된다.
 */
public record LiveSnapshotCommand(
	String roomId,
	Long auctionId,
	Long requesterId,
	LiveSnapshotImage image
) {

	public LiveSnapshotCommand {
		if (roomId == null || roomId.isBlank()) {
			throw new IllegalArgumentException("roomId must not be blank");
		}
		if (auctionId == null || auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		if (requesterId == null) {
			throw new IllegalArgumentException("requesterId must not be null");
		}
		if (image == null) {
			throw new IllegalArgumentException("image must not be null");
		}
	}
}
