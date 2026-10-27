package com.ssafy.bidlive.media.domain;

import java.time.OffsetDateTime;

/**
 * 방송을 시작한 회원과 진행 상태로 표현한 방 단위 라이브 방송 기록입니다.
 *
 * <p>경매방의 판매자 정보는 `auction` 도메인이 소유하므로, 방송 종료 권한은 이 기록에 남은
 * 방송 개시자를 기준으로 판정한다.
 */
public record LiveBroadcast(
	String roomId,
	Long broadcasterId,
	LiveBroadcastStatus status,
	OffsetDateTime startedAt,
	OffsetDateTime endedAt
) {

	public LiveBroadcast {
		if (roomId == null || roomId.isBlank()) {
			throw new IllegalArgumentException("Live broadcast room id must not be blank");
		}
		if (broadcasterId == null || broadcasterId <= 0) {
			throw new IllegalArgumentException("Live broadcast broadcasterId must be positive");
		}
		if (status == null) {
			throw new IllegalArgumentException("Live broadcast status must not be null");
		}
		if (startedAt == null) {
			throw new IllegalArgumentException("Live broadcast startedAt must not be null");
		}
		if (status == LiveBroadcastStatus.ENDED && endedAt == null) {
			throw new IllegalArgumentException("Ended live broadcast must have endedAt");
		}
		if (status == LiveBroadcastStatus.LIVE && endedAt != null) {
			throw new IllegalArgumentException("Live broadcast must not have endedAt");
		}
	}

	public boolean isStartedBy(Long memberId) {
		return broadcasterId.equals(memberId);
	}

	public boolean isLive() {
		return status == LiveBroadcastStatus.LIVE;
	}
}
