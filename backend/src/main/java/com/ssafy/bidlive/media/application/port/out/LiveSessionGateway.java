package com.ssafy.bidlive.media.application.port.out;

import com.ssafy.bidlive.media.domain.LiveSessionRole;

/**
 * 라이브 방송 세션 생성과 역할별 접속 토큰 발급을 외부 미디어 시스템에 위임하는 포트입니다.
 */
public interface LiveSessionGateway {

	String ensureSession(String roomId);

	String issueToken(String sessionId, LiveSessionRole role);

	/**
	 * 세션과 참가자 연결을 모두 닫는다. 이미 사라진 세션에 대한 종료 요청은 목표가 달성된
	 * 상태이므로 실패로 보지 않으며, 그 밖의 연동 실패는
	 * {@link LiveSessionGatewayException}으로 전달한다.
	 */
	void closeSession(String sessionId);
}
