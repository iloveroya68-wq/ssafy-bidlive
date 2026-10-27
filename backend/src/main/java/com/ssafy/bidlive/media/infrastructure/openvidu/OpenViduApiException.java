package com.ssafy.bidlive.media.infrastructure.openvidu;

import com.ssafy.bidlive.media.application.port.out.LiveSessionGatewayException;

/** OpenVidu REST API 호출 실패를 상위 계층이 다룰 수 있는 하나의 예외로 변환합니다. */
public class OpenViduApiException extends LiveSessionGatewayException {

	public OpenViduApiException(String message) {
		super(message);
	}

	public OpenViduApiException(String message, Throwable cause) {
		super(message, cause);
	}
}
