package com.ssafy.bidlive.auth.application;

import com.ssafy.bidlive.auth.domain.OAuthTokenExchangeErrorCode;

public class OAuthTokenExchangeException extends RuntimeException {

	private final OAuthTokenExchangeErrorCode errorCode;

	public OAuthTokenExchangeException(OAuthTokenExchangeErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public OAuthTokenExchangeErrorCode getErrorCode() {
		return errorCode;
	}
}
