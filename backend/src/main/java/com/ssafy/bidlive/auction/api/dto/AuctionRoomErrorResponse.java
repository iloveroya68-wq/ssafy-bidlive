package com.ssafy.bidlive.auction.api.dto;

/**
 * 경매 명령 API(삭제·수정·시작·입장·연장)의 공통 오류 응답이다.
 *
 * <p>엔드포인트별로 같은 모양의 레코드를 하나씩 두는 대신 하나로 모았다 — 다섯 엔드포인트가 모두
 * `{ code, message }`만 반환하고, 필드가 갈릴 이유가 생기면 그때 나누는 편이 싸다.
 */
public record AuctionRoomErrorResponse(
	String code,
	String message
) {
}
