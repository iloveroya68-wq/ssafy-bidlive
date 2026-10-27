package com.ssafy.bidlive.member.application;

import java.time.Instant;

/** 약관 동의 저장 후 계산한 온보딩 완료 여부와 동의 시각이다. */
public record TermsAgreementResult(
	boolean onboardingCompleted,
	Instant agreedAt
) {
}
