package com.ssafy.bidlive.auction.domain;

/**
 * 경매 결과 조회(DEAL-06·DEAL-08) 오류 코드.
 *
 * <p>명세 「오류 응답」표에는 403 {@code AUCTION_RESULT_ACCESS_DENIED}도 있지만 쓰지 않는다.
 * 비공개 방은 <b>존재 자체를 감춰야</b> 하므로 삭제(ROOM-04)·입장(ROOM-07)과 같이 404로 답한다 —
 * 403을 주면 "그 방은 있는데 네가 못 볼 뿐"이 새어 나간다.
 *
 * <p>409의 이름이 {@code PREDICTION_RESULT_NOT_AVAILABLE}인 것은 명세 표기 그대로다.
 * 경매 결과 전반을 가리키는데 예측 접두사가 붙어 어색하지만, 프론트가 이 문자열을 보고
 * 「결과 확정 전」을 판정하므로(`resultSource.ts`의 `isResultNotReady`) 임의로 바꾸지 않는다.
 */
public enum AuctionResultErrorCode {
	INVALID_AUCTION_ID,
	AUCTION_NOT_FOUND,
	PREDICTION_RESULT_NOT_AVAILABLE
}
