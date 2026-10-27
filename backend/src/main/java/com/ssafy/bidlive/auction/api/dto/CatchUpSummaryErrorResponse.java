package com.ssafy.bidlive.auction.api.dto;

/** AI-03 조회·음성 수집 오류의 공통 JSON 형태다. */
public record CatchUpSummaryErrorResponse(String code, String message) {
}
