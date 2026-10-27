package com.ssafy.bidlive.member.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 개별 약관 버전에 대한 회원의 동의 선택을 받는다. */
public record TermsAgreementItemRequest(
	@NotNull @Positive Long termId,
	@NotNull Boolean agreed
) {
}
