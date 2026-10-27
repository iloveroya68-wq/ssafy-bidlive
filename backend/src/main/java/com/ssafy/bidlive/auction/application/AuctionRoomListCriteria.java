package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionRoomListCursor;
import com.ssafy.bidlive.auction.domain.AuctionStatus;

/**
 * 검증을 마친 목록 조회 조건이다. {@code status}는 목록에만, {@code categoryId}·{@code keyword}는
 * 목록과 상태별 개수 집계 양쪽에 적용된다.
 */
public record AuctionRoomListCriteria(
	AuctionStatus status,
	Long categoryId,
	String keyword,
	AuctionRoomListCursor cursor,
	int size
) {
}
