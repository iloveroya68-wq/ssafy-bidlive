package com.ssafy.bidlive.media.infrastructure.redis;

/** 미디어 도메인이 소유하는 Redis Key를 일관된 형식으로 생성합니다. */
public final class MediaRedisKey {

	private static final String VIEWER_SESSIONS_PATTERN =
		"bidlive:media:{%d}:viewer-sessions";

	private MediaRedisKey() {
	}

	public static String viewerSessions(long auctionId) {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId는 양수여야 합니다.");
		}
		return VIEWER_SESSIONS_PATTERN.formatted(auctionId);
	}
}
