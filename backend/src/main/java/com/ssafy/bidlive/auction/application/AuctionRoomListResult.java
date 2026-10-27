package com.ssafy.bidlive.auction.application;

import java.time.Instant;
import java.util.List;

/**
 * 목록 조회 유스케이스의 결과다. {@code serverTime}은 목록과 집계가 끝난 뒤 응답을 만드는 시점의
 * 서버 시각이며, 프론트가 기기 시각 대신 이 값으로 카운트다운을 계산한다.
 */
public record AuctionRoomListResult(
	Instant serverTime,
	List<AuctionRoomSummaryItem> items,
	String nextCursor,
	boolean hasNext,
	AuctionRoomStatusCounts counts
) {

	public AuctionRoomListResult {
		items = List.copyOf(items);
	}
}
