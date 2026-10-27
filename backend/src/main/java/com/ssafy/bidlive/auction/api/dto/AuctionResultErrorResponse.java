package com.ssafy.bidlive.auction.api.dto;

import java.time.Instant;

/** 경매 결과 조회 오류 응답. 다른 auction 엔드포인트와 같은 모양이다 */
public record AuctionResultErrorResponse(
	Instant timestamp,
	int status,
	String code,
	String message
) {
}
