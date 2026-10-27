package com.ssafy.bidlive.deal.api.dto;

/**
 * 거래 API의 공통 오류 응답이다. 프론트는 코드로 분기하지 않고 `message`를 그대로 띄우므로
 * (`useDealAction`) 두 필드가 계약의 전부다.
 */
public record DealErrorResponse(
	String code,
	String message
) {
}
