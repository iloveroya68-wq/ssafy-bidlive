package com.ssafy.bidlive.media.api;

/** 라이브 API 요청에서 인증 회원 식별자를 얻을 수 없는 상태를 나타냅니다. */
public class LiveUnauthenticatedException extends RuntimeException {

	public LiveUnauthenticatedException() {
		super("Authenticated member is required for live API");
	}

	public LiveUnauthenticatedException(Throwable cause) {
		super("Authenticated member is required for live API", cause);
	}
}
