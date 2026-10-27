package com.ssafy.bidlive.member.api.dto;

import java.util.List;

import com.ssafy.bidlive.member.application.TermsQueryResult;

/** 목적별 최신 약관과 회원 동의 여부 목록을 반환한다. */
public record TermsResponse(List<TermsItemResponse> items) {

	public TermsResponse {
		items = List.copyOf(items);
	}

	public static TermsResponse from(TermsQueryResult result) {
		return new TermsResponse(result.items().stream()
			.map(TermsItemResponse::from)
			.toList());
	}
}
