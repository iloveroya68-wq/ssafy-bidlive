package com.ssafy.bidlive.member.api.dto;

import com.ssafy.bidlive.member.application.TermsQueryItem;

/** 약관 조회 응답의 개별 약관 정보를 표현한다. */
public record TermsItemResponse(
	Long termId,
	String code,
	String title,
	String content,
	String version,
	boolean required,
	boolean agreed
) {

	public static TermsItemResponse from(TermsQueryItem item) {
		return new TermsItemResponse(
			item.termId(),
			item.code().name(),
			item.title(),
			item.content(),
			item.version(),
			item.required(),
			item.agreed()
		);
	}
}
