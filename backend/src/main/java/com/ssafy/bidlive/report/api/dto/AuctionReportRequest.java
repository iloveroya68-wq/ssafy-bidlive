package com.ssafy.bidlive.report.api.dto;

import com.ssafy.bidlive.report.application.AuctionReportCommand;

/**
 * 신고 접수 요청이다.
 *
 * <p>{@code reasonCode}를 문자열로 받는 이유: enum으로 바인딩하면 모르는 값에 Jackson이 400을
 * 내는데 본문이 스프링 기본 오류라 명세의 {@code INVALID_REPORT_REQUEST} 코드가 실리지 않는다.
 * 값 판정은 {@link AuctionReportCommand}가 한다.
 *
 * <p>명세의 {@code evidenceUrls}는 받지 않는다 — 이유는 {@link AuctionReportCommand} 참조.
 */
public record AuctionReportRequest(
	String reasonCode,
	String description
) {

	public AuctionReportCommand toCommand() {
		return AuctionReportCommand.of(reasonCode, description);
	}
}
