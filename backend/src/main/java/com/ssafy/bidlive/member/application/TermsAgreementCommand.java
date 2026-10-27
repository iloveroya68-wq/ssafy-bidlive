package com.ssafy.bidlive.member.application;

import java.util.List;

/** 회원이 제출한 약관 버전별 동의 선택 목록이다. */
public record TermsAgreementCommand(List<TermDecision> agreements) {

	public TermsAgreementCommand {
		agreements = agreements == null ? null : List.copyOf(agreements);
	}

	/** 개별 약관 버전에 대한 동의 선택이다. */
	public record TermDecision(Long termId, Boolean agreed) {
	}
}
