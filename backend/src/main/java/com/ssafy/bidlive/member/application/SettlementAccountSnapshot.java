package com.ssafy.bidlive.member.application;

import java.time.Instant;

/** 영속성 계층에서 읽은 마스킹된 정산 계좌 요약이다. */
public record SettlementAccountSnapshot(
	String bankCode,
	String maskedAccountNumber,
	String accountHolderName,
	boolean verified,
	Instant updatedAt
) {
}
