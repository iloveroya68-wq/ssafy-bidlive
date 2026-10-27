package com.ssafy.bidlive.auction.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/** 낙찰 경매 이력의 종료 시각·경매 ID 최신순 키셋 페이징 커서다. */
public record WonAuctionHistoryCursor(Instant endedAt, long auctionId) {

	private static final char DELIMITER = '|';

	public WonAuctionHistoryCursor {
		if (endedAt == null || auctionId <= 0) {
			throw new IllegalArgumentException("won auction cursor");
		}
	}

	public String encode() {
		String payload = endedAt + String.valueOf(DELIMITER) + auctionId;
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * @throws IllegalArgumentException 커서 형식이나 값이 유효하지 않을 때
	 */
	public static WonAuctionHistoryCursor decode(String encoded) {
		try {
			String payload = new String(
				Base64.getUrlDecoder().decode(encoded),
				StandardCharsets.UTF_8
			);
			int delimiter = payload.lastIndexOf(DELIMITER);
			if (delimiter <= 0 || delimiter == payload.length() - 1) {
				throw new IllegalArgumentException("won auction cursor");
			}
			return new WonAuctionHistoryCursor(
				Instant.parse(payload.substring(0, delimiter)),
				Long.parseLong(payload.substring(delimiter + 1))
			);
		} catch (DateTimeParseException | NumberFormatException exception) {
			throw new IllegalArgumentException(
				"won auction cursor",
				exception
			);
		}
	}
}
