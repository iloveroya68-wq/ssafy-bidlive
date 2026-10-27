package com.ssafy.bidlive.member.application;

/** 정산 계좌 등록·변경에 필요한 외부 입력을 전달한다. */
public record SettlementAccountUpdateCommand(
	String bankCode,
	String accountNumber,
	String accountHolderName
) {
}
