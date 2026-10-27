package com.ssafy.bidlive.deal.domain;

/**
 * 거래 API의 오류 코드다.
 *
 * <p>DEAL-05 세 문서에는 오류 표가 없다(「내 거래 할 일」만 401을 적었다). 그래서 권한·상태·검증
 * 오류 코드는 이 레포의 기존 명명(`NOT_AUCTION_SELLER`·`INVALID_AUCTION_STATUS`)을 따라 정했다.
 * 명세에 표가 추가되면 그쪽을 정본으로 맞춘다 — 프론트는 코드로 분기하지 않고 `message`만 띄우므로
 * (`useDealAction`) 이름이 바뀌어도 화면은 깨지지 않는다.
 */
public enum DealErrorCode {
	UNAUTHORIZED,
	DEAL_NOT_FOUND,
	NOT_DEAL_BUYER,
	NOT_DEAL_SELLER,
	INVALID_DEAL_STATUS,
	INVALID_SHIPPING_ADDRESS,
	/** DEAL-01 목록의 `role`·`status` 필터가 알아볼 수 없는 값일 때 */
	INVALID_DEAL_FILTER,
	/** DEAL-01 목록의 커서가 깨졌을 때 */
	INVALID_CURSOR
}
