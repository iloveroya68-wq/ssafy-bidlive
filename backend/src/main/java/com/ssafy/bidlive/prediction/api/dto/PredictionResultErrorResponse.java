package com.ssafy.bidlive.prediction.api.dto;

/** PRED-02 조회 실패 코드와 사용자 메시지를 반환한다. */
public record PredictionResultErrorResponse(String code, String message) {
}
