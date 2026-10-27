package com.ssafy.bidlive.auction.application;

import java.util.List;

/** 커서 한 페이지 분량의 조회 결과다. 다음 커서는 정렬 규칙을 아는 조회 계층이 만든다. */
public record AuctionRoomListSlice(
	List<AuctionRoomRow> rows,
	String nextCursor,
	boolean hasNext
) {

	public AuctionRoomListSlice {
		rows = List.copyOf(rows);
	}
}
