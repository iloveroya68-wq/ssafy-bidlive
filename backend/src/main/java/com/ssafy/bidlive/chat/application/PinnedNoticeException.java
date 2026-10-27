package com.ssafy.bidlive.chat.application;

import java.util.Objects;

import com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode;

/** 고정 공지 유스케이스 실패를 안정적인 오류 코드와 함께 전달합니다. */
public class PinnedNoticeException extends RuntimeException {

	private final PinnedNoticeErrorCode errorCode;

	public PinnedNoticeException(PinnedNoticeErrorCode errorCode) {
		this(errorCode, null);
	}

	public PinnedNoticeException(
		PinnedNoticeErrorCode errorCode,
		Throwable cause
	) {
		super(Objects.requireNonNull(errorCode, "errorCode").name(), cause);
		this.errorCode = errorCode;
	}

	public PinnedNoticeErrorCode errorCode() {
		return errorCode;
	}
}
