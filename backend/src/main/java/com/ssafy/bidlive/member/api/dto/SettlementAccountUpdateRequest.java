package com.ssafy.bidlive.member.api.dto;

import com.ssafy.bidlive.member.application.SettlementAccountUpdateCommand;

/** 정산 계좌 등록·변경 요청을 Application 명령으로 변환한다. */
public record SettlementAccountUpdateRequest(
	String bankCode,
	String accountNumber,
	String accountHolderName
) {

	public SettlementAccountUpdateCommand toCommand() {
		return new SettlementAccountUpdateCommand(
			bankCode,
			accountNumber,
			accountHolderName
		);
	}
}
