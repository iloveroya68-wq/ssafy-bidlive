package com.ssafy.bidlive.prediction.api.dto;

/** 예측 성과 조회 API의 오류 코드와 사용자 메시지를 반환한다. */
public record PredictionAchievementErrorResponse(String code, String message) {
}
