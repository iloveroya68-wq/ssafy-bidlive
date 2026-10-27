package com.ssafy.bidlive.report.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.report.application.AuctionReportResult;

/** 접수 확인 응답이다. 검토 결과는 담지 않는다. */
public record AuctionReportResponse(
	Long reportId,
	String status,
	OffsetDateTime receivedAt
) {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public static AuctionReportResponse from(AuctionReportResult result) {
		return new AuctionReportResponse(
			result.reportId(),
			result.status(),
			atKorea(result.receivedAt())
		);
	}

	static OffsetDateTime atKorea(Instant instant) {
		return instant == null ? null : instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}
}
