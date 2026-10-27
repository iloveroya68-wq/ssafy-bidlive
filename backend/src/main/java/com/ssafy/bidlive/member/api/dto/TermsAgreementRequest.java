package com.ssafy.bidlive.member.api.dto;

import java.util.List;

import com.ssafy.bidlive.member.application.TermsAgreementCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** 온보딩 약관 버전별 동의 선택 목록을 받는다. */
public record TermsAgreementRequest(
	@NotEmpty List<@NotNull @Valid TermsAgreementItemRequest> agreements
) {

	public TermsAgreementCommand toCommand() {
		return new TermsAgreementCommand(agreements.stream()
			.map(item -> new TermsAgreementCommand.TermDecision(
				item.termId(),
				item.agreed()
			))
			.toList());
	}
}
