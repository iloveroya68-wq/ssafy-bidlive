package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.ProfileUpdateErrorCode;

/** 프로필 수정 요청을 완료할 수 없을 때 명세 오류 코드를 전달한다. */
public class ProfileUpdateException extends RuntimeException {

	private final ProfileUpdateErrorCode errorCode;

	public ProfileUpdateException(ProfileUpdateErrorCode errorCode) {
		this(errorCode, null);
	}

	public ProfileUpdateException(
		ProfileUpdateErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public ProfileUpdateErrorCode getErrorCode() {
		return errorCode;
	}
}
