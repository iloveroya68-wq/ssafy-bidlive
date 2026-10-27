package com.ssafy.bidlive.media.api.dto;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.ssafy.bidlive.media.application.ViewerCountUpdate;

/** VIEWER_COUNT WebSocket 이벤트의 활성 세션 수와 측정 정보를 표현합니다. */
public record ViewerCountData(
	long viewerCount,
	String countedBy,
	OffsetDateTime measuredAt
) {

	private static final String STOMP_SESSION = "STOMP_SESSION";

	public ViewerCountData {
		if (viewerCount < 0) {
			throw new IllegalArgumentException("viewerCount는 음수일 수 없습니다.");
		}
		if (countedBy == null || countedBy.isBlank()) {
			throw new IllegalArgumentException("countedBy는 비어 있을 수 없습니다.");
		}
		Objects.requireNonNull(measuredAt, "measuredAt");
	}

	public static ViewerCountData from(ViewerCountUpdate update) {
		Objects.requireNonNull(update, "update");
		return new ViewerCountData(
			update.viewerCount(),
			STOMP_SESSION,
			update.measuredAt()
		);
	}
}
