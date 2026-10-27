package com.ssafy.bidlive.media.application;

import com.ssafy.bidlive.media.domain.LiveTokenFailureCode;

/** 라이브 접속 토큰 발급이 방 접근 권한 판정에서 거부된 상태를 나타냅니다. */
public class LiveTokenRejectedException extends RuntimeException {

	private final LiveTokenFailureCode failureCode;

	public LiveTokenRejectedException(LiveTokenFailureCode failureCode) {
		super("Live token issue rejected: " + failureCode);
		this.failureCode = failureCode;
	}

	public LiveTokenFailureCode getFailureCode() {
		return failureCode;
	}
}
