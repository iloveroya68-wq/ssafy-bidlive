package com.ssafy.bidlive.media.application.port.out;

/**
 * 미디어 서버 연동 실패를 유스케이스가 다룰 수 있게 만든 포트 수준 예외입니다.
 *
 * <p>어댑터의 기술별 예외를 이 타입으로 묶어 두면 `application`이 특정 미디어 서버 구현을
 * import하지 않고도 외부 호출 실패만 골라 처리할 수 있다.
 */
public class LiveSessionGatewayException extends RuntimeException {

	public LiveSessionGatewayException(String message) {
		super(message);
	}

	public LiveSessionGatewayException(String message, Throwable cause) {
		super(message, cause);
	}
}
