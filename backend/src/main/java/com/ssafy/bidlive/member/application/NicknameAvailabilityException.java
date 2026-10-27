package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.NicknameAvailabilityErrorCode;

/** 닉네임 사용 가능 여부 요청을 처리할 수 없을 때 명세 오류 코드를 전달한다. */
public class NicknameAvailabilityException extends RuntimeException {

	private final NicknameAvailabilityErrorCode errorCode;

	public NicknameAvailabilityException(NicknameAvailabilityErrorCode errorCode) {
		this(errorCode, null);
	}

	public NicknameAvailabilityException(
		NicknameAvailabilityErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public NicknameAvailabilityErrorCode getErrorCode() {
		return errorCode;
	}
}
