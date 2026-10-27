package com.ssafy.bidlive.member.api.dto;

/** 정산 계좌 조회 오류 코드와 메시지를 반환한다. */
public record SettlementAccountQueryErrorResponse(
	String code,
	String message
) {
}
