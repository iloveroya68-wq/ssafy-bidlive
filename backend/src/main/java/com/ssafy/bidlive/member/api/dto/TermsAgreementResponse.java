package com.ssafy.bidlive.member.api.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.member.application.TermsAgreementResult;

/** 약관 동의 처리 후 온보딩 완료 여부와 동의 시각을 반환한다. */
public record TermsAgreementResponse(
	boolean onboardingCompleted,
	OffsetDateTime agreedAt
) {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public static TermsAgreementResponse from(TermsAgreementResult result) {
		return new TermsAgreementResponse(
			result.onboardingCompleted(),
			result.agreedAt().atZone(KOREA_ZONE).toOffsetDateTime()
		);
	}
}
