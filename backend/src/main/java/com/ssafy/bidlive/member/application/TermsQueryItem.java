package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.TermCode;

/** 약관 조회 결과의 개별 최신 약관과 회원 동의 여부다. */
public record TermsQueryItem(
	Long termId,
	TermCode code,
	String title,
	String content,
	String version,
	boolean required,
	boolean agreed
) {
}
