package com.ssafy.bidlive.deal.application;

import com.ssafy.bidlive.deal.domain.DealListCursor;
import com.ssafy.bidlive.deal.domain.DealRole;

/**
 * 내 거래 목록 조회 조건. {@code role}·{@code status}는 선택이고 생략하면 전체다.
 *
 * <p>{@code status}는 문자열이다 — 컨트롤러가 {@code DealStatus}로 <b>검증만</b> 하고 값은 그대로
 * 넘긴다. DB에 CHECK가 없어 조회 결과에는 enum에 없는 값도 올 수 있기 때문이다.
 */
public record DealListCriteria(
	long memberId,
	DealRole role,
	String status,
	DealListCursor cursor,
	int size
) {
}
