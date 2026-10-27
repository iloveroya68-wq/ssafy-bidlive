package com.ssafy.bidlive.member.application;

import java.util.List;

/** 현재 회원에게 적용되는 최신 약관 목록 조회 결과다. */
public record TermsQueryResult(List<TermsQueryItem> items) {

	public TermsQueryResult {
		items = List.copyOf(items);
	}
}
