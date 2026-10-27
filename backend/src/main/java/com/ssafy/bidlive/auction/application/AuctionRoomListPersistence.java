package com.ssafy.bidlive.auction.application;

/** 경매방 목록·상태별 개수 조회 영속성 계약이다. */
public interface AuctionRoomListPersistence {

	AuctionRoomListSlice findRooms(AuctionRoomListCriteria criteria);

	/** {@code criteria}의 카테고리·검색어만 적용한다 — 상태 필터와 커서는 집계에 영향을 주지 않는다. */
	AuctionRoomStatusCounts countByStatus(AuctionRoomListCriteria criteria);

	boolean existsCategory(long categoryId);
}
