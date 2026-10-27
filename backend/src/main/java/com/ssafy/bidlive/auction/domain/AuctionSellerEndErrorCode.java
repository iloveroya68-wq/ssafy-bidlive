package com.ssafy.bidlive.auction.domain;

/**
 * ROOM-09 판매자 종료 실패 사유다.
 *
 * <p>운영 강제 종료(`AuctionForceEndErrorCode`)와 코드를 나눈 이유: 두 API의 판정 규칙이 다르다.
 * 이쪽은 아무 회원이나 부를 수 있는 경로라 인증·소유 판정이 앞에 붙고, 삭제된 방을 404로 감춘다.
 * 운영 종료는 경로 자체가 `hasRole("ADMIN")`이라 그 둘이 아예 없다.
 */
public enum AuctionSellerEndErrorCode {
	INVALID_AUCTION_ID,
	UNAUTHORIZED,
	AUCTION_NOT_FOUND,
	/** 내 방이 아니다. 존재는 인정하고 권한만 거절한다 — 이미 소유자 판정을 통과한 조회가 아니다 */
	NOT_AUCTION_SELLER,
	/** 이미 종료됐다. `WAITING`·`LIVE`만 종료할 수 있다 */
	AUCTION_NOT_ENDABLE
}
