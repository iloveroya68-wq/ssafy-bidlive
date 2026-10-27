package com.ssafy.bidlive.media.api.dto;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.media.domain.LiveSessionRole;
import com.ssafy.bidlive.media.domain.LiveSessionToken;

/** 라이브 토큰 발급 결과를 프로젝트 공통 `type`·`data` 형식으로 감싸 반환합니다. */
public record LiveTokenResponse(
	LiveTokenEventType type,
	LiveTokenData data
) {

	public static LiveTokenResponse from(LiveSessionToken sessionToken) {
		return new LiveTokenResponse(
			LiveTokenEventType.from(sessionToken.role()),
			new LiveTokenData(
				sessionToken.roomId(),
				sessionToken.sessionId(),
				sessionToken.token(),
				sessionToken.role(),
				sessionToken.createdAt()
			)
		);
	}

	/**
	 * {@code roomId}는 명세 예시의 숫자 형태 대신 확장성을 고려해 String으로 확정했다.
	 * PathVariable과 응답이 같은 타입을 쓰며, 확정된 계약은 추후 {@code docs/api/media}에 반영한다.
	 */
	public record LiveTokenData(
		String roomId,
		String sessionId,
		String token,
		LiveSessionRole role,
		OffsetDateTime createdAt
	) {
	}
}
