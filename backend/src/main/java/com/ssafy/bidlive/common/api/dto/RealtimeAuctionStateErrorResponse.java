package com.ssafy.bidlive.common.api.dto;

import java.time.Instant;

/** realtime-state 조회 실패의 HTTP 상태와 안정적인 오류 코드를 반환합니다. */
public record RealtimeAuctionStateErrorResponse(
	Instant timestamp,
	int status,
	RealtimeAuctionStateErrorCode code,
	String message
) {
}
