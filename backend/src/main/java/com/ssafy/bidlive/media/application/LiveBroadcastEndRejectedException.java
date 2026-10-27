package com.ssafy.bidlive.media.application;

import com.ssafy.bidlive.media.domain.LiveBroadcastEndFailureCode;

/** 라이브 방송 종료가 거부된 사유를 실패 코드와 함께 API 계층으로 전달합니다. */
public class LiveBroadcastEndRejectedException extends RuntimeException {

	private final LiveBroadcastEndFailureCode failureCode;

	public LiveBroadcastEndRejectedException(LiveBroadcastEndFailureCode failureCode) {
		super("Live broadcast end rejected: " + failureCode);
		this.failureCode = failureCode;
	}

	public LiveBroadcastEndRejectedException(
		LiveBroadcastEndFailureCode failureCode,
		Throwable cause
	) {
		super("Live broadcast end rejected: " + failureCode, cause);
		this.failureCode = failureCode;
	}

	public LiveBroadcastEndFailureCode getFailureCode() {
		return failureCode;
	}
}
