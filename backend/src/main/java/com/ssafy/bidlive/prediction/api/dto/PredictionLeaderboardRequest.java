package com.ssafy.bidlive.prediction.api.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** PRED-05 일일 리더보드의 조회 날짜와 커서 페이지 조건을 입력받는다. */
public record PredictionLeaderboardRequest(
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate date,
	@Min(1)
	@Max(100)
	Integer size,
	String cursor
) {
	private static final int DEFAULT_SIZE = 10;

	public PredictionLeaderboardRequest {
		if (size == null) {
			size = DEFAULT_SIZE;
		}
	}
}
