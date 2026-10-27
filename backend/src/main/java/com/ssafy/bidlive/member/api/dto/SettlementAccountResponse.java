package com.ssafy.bidlive.member.api.dto;

import java.time.Instant;

import com.ssafy.bidlive.member.application.SettlementAccountResult;

/** 계좌번호 원문 없이 반환하는 정산 계좌 요약이다. */
public record SettlementAccountResponse(
	String bankCode,
	String bankName,
	String maskedAccountNumber,
	String accountHolderName,
	boolean verified,
	Instant updatedAt
) {

	public static SettlementAccountResponse from(
		SettlementAccountResult result
	) {
		return new SettlementAccountResponse(
			result.bankCode(),
			result.bankName(),
			result.maskedAccountNumber(),
			result.accountHolderName(),
			result.verified(),
			result.updatedAt()
		);
	}
}
