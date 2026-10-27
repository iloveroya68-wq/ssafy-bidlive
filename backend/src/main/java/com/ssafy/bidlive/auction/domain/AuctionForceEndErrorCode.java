package com.ssafy.bidlive.auction.domain;

/** 운영 강제 종료(DEAL-09) 실패 사유다. */
public enum AuctionForceEndErrorCode {
	INVALID_AUCTION_ID,
	INVALID_REASON,
	AUCTION_NOT_FOUND,
	/** 이미 종료됐거나 삭제된 방. 명세: `WAITING`·`LIVE`만 강제 종료할 수 있다. */
	AUCTION_NOT_ENDABLE
}
