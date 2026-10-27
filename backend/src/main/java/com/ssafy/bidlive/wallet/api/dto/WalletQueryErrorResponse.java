package com.ssafy.bidlive.wallet.api.dto;

/** 지갑 조회 실패 코드를 전달하는 오류 응답이다. */
public record WalletQueryErrorResponse(
	String code,
	String message
) {
}
