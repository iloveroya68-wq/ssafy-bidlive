package com.ssafy.bidlive.report.application;

import java.time.Instant;

/**
 * 접수 결과다. 검토 결과나 강제 종료 여부는 담지 않는다 — 명세: *"신고 검토 결과나 강제 종료 여부는
 * 응답에 포함하지 않습니다"*. 신고자에게 처리 과정을 노출하지 않는 것이 정책이다.
 */
public record AuctionReportResult(
	long reportId,
	String status,
	Instant receivedAt
) {
}
