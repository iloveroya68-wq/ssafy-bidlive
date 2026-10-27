package com.ssafy.bidlive.media.api.dto;

import com.ssafy.bidlive.media.domain.LiveSessionRole;

/** 라이브 토큰 발급 응답의 공통 `type` 값이며 이름이 그대로 직렬화됩니다. */
public enum LiveTokenEventType {

	LIVE_PUBLISHER_TOKEN_ISSUED,
	LIVE_SUBSCRIBER_TOKEN_ISSUED;

	public static LiveTokenEventType from(LiveSessionRole role) {
		return switch (role) {
			case PUBLISHER -> LIVE_PUBLISHER_TOKEN_ISSUED;
			case SUBSCRIBER -> LIVE_SUBSCRIBER_TOKEN_ISSUED;
		};
	}
}
