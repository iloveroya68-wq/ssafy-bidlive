package com.ssafy.bidlive.member.domain;

/** 정산 계좌 등록·변경 실패를 외부 계약 코드로 분류한다. */
public enum SettlementAccountUpdateErrorCode {
	INVALID_BANK_CODE,
	INVALID_ACCOUNT_NUMBER,
	UNAUTHORIZED,
	MEMBER_NOT_FOUND,
	SETTLEMENT_ACCOUNT_CHANGE_BLOCKED,
	SETTLEMENT_ACCOUNT_UPDATE_FAILED
}
