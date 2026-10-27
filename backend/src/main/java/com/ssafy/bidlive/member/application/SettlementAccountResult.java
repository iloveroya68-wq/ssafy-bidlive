package com.ssafy.bidlive.member.application;

import java.time.Instant;

/** 은행 이름까지 결합한 정산 계좌 조회 결과다. */
public record SettlementAccountResult(
	String bankCode,
	String bankName,
	String maskedAccountNumber,
	String accountHolderName,
	boolean verified,
	Instant updatedAt
) {
}
