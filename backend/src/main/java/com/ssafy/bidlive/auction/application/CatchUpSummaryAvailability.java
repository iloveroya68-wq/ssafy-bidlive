package com.ssafy.bidlive.auction.application;

/** 방 입장 응답이 요약 조회 버튼 노출 여부를 판단하는 읽기 계약이다. */
public interface CatchUpSummaryAvailability {

	boolean isAvailable(long auctionId);
}
