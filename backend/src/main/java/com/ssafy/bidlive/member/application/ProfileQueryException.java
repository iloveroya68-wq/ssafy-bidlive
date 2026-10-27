package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.ProfileQueryErrorCode;

public class ProfileQueryException extends RuntimeException {

	private final ProfileQueryErrorCode errorCode;

	public ProfileQueryException(ProfileQueryErrorCode errorCode) {
		this.errorCode = errorCode;
	}

	public ProfileQueryException(
		ProfileQueryErrorCode errorCode,
		Throwable cause
	) {
		super(cause);
		this.errorCode = errorCode;
	}

	public ProfileQueryErrorCode getErrorCode() {
		return errorCode;
	}
}
