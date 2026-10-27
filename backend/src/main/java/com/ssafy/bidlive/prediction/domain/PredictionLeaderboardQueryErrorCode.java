package com.ssafy.bidlive.prediction.domain;

/** PRED-05 리더보드 조회 요청이 거부되는 계약상 오류를 구분한다. */
public enum PredictionLeaderboardQueryErrorCode {
	INVALID_RANKING_DATE,
	INVALID_PAGE_SIZE,
	INVALID_CURSOR,
	UNAUTHORIZED
}
