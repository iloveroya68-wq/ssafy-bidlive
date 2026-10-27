package com.ssafy.bidlive.common.api.dto;

/** 경매 실시간 상태 스냅샷 조회 실패 시 공개하는 안정적인 오류 코드입니다. */
public enum RealtimeAuctionStateErrorCode {
	AUCTION_NOT_FOUND,
	AUCTION_STATE_UNAVAILABLE
}
