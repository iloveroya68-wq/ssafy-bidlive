package com.ssafy.bidlive.deal.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 내 거래 목록의 최신순 키셋 페이징 커서.
 *
 * <p>정렬 키가 시각 하나면 같은 시각의 행에서 페이지가 겹치거나 빠진다 —
 * {@code dealId}를 타이브레이커로 함께 싣는다(다른 이력 커서들과 같은 규약).
 */
public record DealListCursor(Instant sortedAt, long dealId) {

	private static final char DELIMITER = '|';

	public DealListCursor {
		if (sortedAt == null || dealId <= 0) {
			throw new IllegalArgumentException("deal list cursor");
		}
	}

	public String encode() {
		String payload = sortedAt + String.valueOf(DELIMITER) + dealId;
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	/** @throws IllegalArgumentException 커서 형식이나 값이 유효하지 않을 때 */
	public static DealListCursor decode(String encoded) {
		try {
			String payload = new String(
				Base64.getUrlDecoder().decode(encoded),
				StandardCharsets.UTF_8
			);
			int delimiter = payload.lastIndexOf(DELIMITER);
			if (delimiter <= 0 || delimiter == payload.length() - 1) {
				throw new IllegalArgumentException("deal list cursor");
			}
			return new DealListCursor(
				Instant.parse(payload.substring(0, delimiter)),
				Long.parseLong(payload.substring(delimiter + 1))
			);
		} catch (DateTimeParseException | NumberFormatException exception) {
			throw new IllegalArgumentException("deal list cursor", exception);
		}
	}
}
