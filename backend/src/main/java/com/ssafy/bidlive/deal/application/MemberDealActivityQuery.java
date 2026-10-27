package com.ssafy.bidlive.deal.application;

/** 회원의 완료된 판매·구매 건수를 제공하는 공개 조회 계약이다. */
public interface MemberDealActivityQuery {

	MemberDealActivity query(Long memberId);
}
