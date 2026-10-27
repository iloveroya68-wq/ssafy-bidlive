package com.ssafy.bidlive.auction.application;

/** 카테고리·검색어 조건에 해당하는 상태별 전체 방 개수다. 현재 페이지 개수가 아니다. */
public record AuctionRoomStatusCounts(
	long live,
	long waiting
) {
}
