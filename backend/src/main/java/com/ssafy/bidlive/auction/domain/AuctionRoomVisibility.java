package com.ssafy.bidlive.auction.domain;

/**
 * 경매방 공개 범위다 (`auction_room.visibility`, DB CHECK로 두 값만 허용된다).
 *
 * <p>공개 API가 판정에 쓰는 것은 "공개인가" 하나뿐이라 {@link #isPublic(String)}만 노출한다.
 * 상세·목록·다가오는 방은 비공개 방을 아예 반환하지 않고, 수정·삭제는 비판매자에게 404로 숨긴다
 * (S15P11A704-482). 판매자 본인에게는 제약이 아니다.
 */
public enum AuctionRoomVisibility {
	PUBLIC,
	PRIVATE;

	/** 컬럼에 DB CHECK가 있어도 알 수 없는 값이 오면 공개가 아닌 쪽으로 본다. */
	public static boolean isPublic(String value) {
		return PUBLIC.name().equals(value);
	}
}
