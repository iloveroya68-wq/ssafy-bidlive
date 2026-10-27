package com.ssafy.bidlive.member.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.bidlive.member.application.SettlementAccountQueryResult;

/** 계좌 미등록 상태에서도 account 필드를 명시적으로 반환한다. */
public record SettlementAccountQueryResponse(
	@JsonInclude(JsonInclude.Include.ALWAYS)
	SettlementAccountResponse account
) {

	public static SettlementAccountQueryResponse from(
		SettlementAccountQueryResult result
	) {
		return new SettlementAccountQueryResponse(
			result.account() == null
				? null
				: SettlementAccountResponse.from(result.account())
		);
	}
}
