package com.ssafy.bidlive.auction.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DEAL-09 요청 본문.
 *
 * <p>`adminNote`는 명세 예시에만 있고 처리 규칙이 없다 — 받되 쓰지 않는다.
 * 감사 로그로 남길 자리가 정해지면 그때 소비한다.
 */
public record ForceEndRequest(
	@NotBlank String reason,
	String adminNote
) {
}
