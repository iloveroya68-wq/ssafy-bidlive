package com.ssafy.bidlive.media.api.dto;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.media.domain.LiveSnapshot;

/** 스냅샷 저장 결과를 프로젝트 공통 `type`·`data` 형식으로 감싸 반환합니다. */
public record LiveSnapshotResponse(
	LiveSnapshotEventType type,
	LiveSnapshotData data
) {

	public static LiveSnapshotResponse from(LiveSnapshot snapshot) {
		return new LiveSnapshotResponse(
			LiveSnapshotEventType.LIVE_SNAPSHOT_CAPTURED,
			new LiveSnapshotData(
				snapshot.roomId(),
				snapshot.auctionId(),
				snapshot.imageUrl(),
				snapshot.capturedAt()
			)
		);
	}

	/** `roomId`는 LIVE-01·LIVE-02와 같은 String 계약을 유지한다. */
	public record LiveSnapshotData(
		String roomId,
		Long auctionId,
		String snapshotUrl,
		OffsetDateTime capturedAt
	) {
	}
}
