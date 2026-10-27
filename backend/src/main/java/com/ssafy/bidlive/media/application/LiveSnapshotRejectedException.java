package com.ssafy.bidlive.media.application;

import com.ssafy.bidlive.media.domain.LiveSnapshotFailureCode;

/** 낙찰 스냅샷 저장이 거부된 사유를 실패 코드와 함께 API 계층으로 전달합니다. */
public class LiveSnapshotRejectedException extends RuntimeException {

	private final LiveSnapshotFailureCode failureCode;

	public LiveSnapshotRejectedException(LiveSnapshotFailureCode failureCode) {
		super("Live snapshot rejected: " + failureCode);
		this.failureCode = failureCode;
	}

	public LiveSnapshotRejectedException(
		LiveSnapshotFailureCode failureCode,
		Throwable cause
	) {
		super("Live snapshot rejected: " + failureCode, cause);
		this.failureCode = failureCode;
	}

	public LiveSnapshotFailureCode getFailureCode() {
		return failureCode;
	}
}
