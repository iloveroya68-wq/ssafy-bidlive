package com.ssafy.bidlive.prediction.api.dto;

/** PRED-01 실패 코드와 사용자 메시지를 반환한다. */
public record PredictionErrorResponse(String code, String message) {
}
