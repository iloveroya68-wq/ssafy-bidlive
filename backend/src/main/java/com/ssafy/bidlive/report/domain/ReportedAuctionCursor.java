package com.ssafy.bidlive.report.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 신고 접수 경매 목록의 최신순 키셋 페이징 커서.
 *
 * <p>정렬 키가 시각 하나면 같은 시각의 행에서 페이지가 겹치거나 빠진다 —
 * {@code auctionId}를 타이브레이커로 함께 싣는다({@code DealListCursor}와 같은 규약).
 */
public record ReportedAuctionCursor(Instant latestReportedAt, long auctionId) {

	private static final char DELIMITER = '|';

	public ReportedAuctionCursor {
		if (latestReportedAt == null || auctionId <= 0) {
			throw new IllegalArgumentException("reported auction cursor");
		}
	}

	public String encode() {
		String payload = latestReportedAt + String.valueOf(DELIMITER) + auctionId;
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	/** @throws IllegalArgumentException 커서 형식이나 값이 유효하지 않을 때 */
	public static ReportedAuctionCursor decode(String encoded) {
		try {
			String payload = new String(
				Base64.getUrlDecoder().decode(encoded),
				StandardCharsets.UTF_8
			);
			int delimiter = payload.lastIndexOf(DELIMITER);
			if (delimiter <= 0 || delimiter == payload.length() - 1) {
				throw new IllegalArgumentException("reported auction cursor");
			}
			return new ReportedAuctionCursor(
				Instant.parse(payload.substring(0, delimiter)),
				Long.parseLong(payload.substring(delimiter + 1))
			);
		} catch (DateTimeParseException | NumberFormatException exception) {
			throw new IllegalArgumentException("reported auction cursor", exception);
		}
	}
}
