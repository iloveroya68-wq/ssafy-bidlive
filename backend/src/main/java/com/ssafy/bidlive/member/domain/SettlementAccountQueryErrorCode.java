package com.ssafy.bidlive.member.domain;

/** 정산 계좌 조회 API에서 반환하는 오류 코드다. */
public enum SettlementAccountQueryErrorCode {
	UNAUTHORIZED,
	MEMBER_NOT_FOUND,
	SETTLEMENT_ACCOUNT_UNAVAILABLE
}
