package com.ssafy.bidlive.media.domain;

import java.time.OffsetDateTime;

/** 방송 종료가 확정되었음을 알리는 도메인 이벤트이며 시청자 브로드캐스트의 발생 근거입니다. */
public record LiveBroadcastEndedEvent(
	String roomId,
	OffsetDateTime endedAt
) {

	public LiveBroadcastEndedEvent {
		if (roomId == null || roomId.isBlank()) {
			throw new IllegalArgumentException("Live broadcast room id must not be blank");
		}
		if (endedAt == null) {
			throw new IllegalArgumentException("Live broadcast endedAt must not be null");
		}
	}
}
