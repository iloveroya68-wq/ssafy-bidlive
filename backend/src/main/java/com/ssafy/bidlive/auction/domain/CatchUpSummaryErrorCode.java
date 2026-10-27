package com.ssafy.bidlive.auction.domain;

/** 중간 입장 요약 조회와 판매자 음성 수집에서 사용하는 공개 실패 코드다. */
public enum CatchUpSummaryErrorCode {
	UNAUTHORIZED,
	ROOM_ACCESS_DENIED,
	AUCTION_ROOM_NOT_FOUND,
	AUCTION_NOT_STARTED,
	NOT_AUCTION_SELLER,
	INVALID_AUDIO_CHUNK,
	UNSUPPORTED_AUDIO_TYPE,
	AUDIO_CHUNK_TOO_LARGE
}
