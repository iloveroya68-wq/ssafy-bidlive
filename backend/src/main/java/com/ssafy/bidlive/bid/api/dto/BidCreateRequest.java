package com.ssafy.bidlive.bid.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 금액 직접 입력 입찰 요청의 필수 양수 금액 형식을 정의한다. */
public record BidCreateRequest(
	@NotNull
	@Positive
	Long amount
) {
}
