package com.ssafy.bidlive.auction.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/** 참여 경매 이력의 종료 시각·경매 ID 최신순 키셋 페이징 커서다. */
public record ParticipatedAuctionHistoryCursor(
	Instant sortedAt,
	long auctionId
) {

	private static final char DELIMITER = '|';

	public ParticipatedAuctionHistoryCursor {
		if (sortedAt == null || auctionId <= 0) {
			throw new IllegalArgumentException("participated auction cursor");
		}
	}

	public String encode() {
		String payload = sortedAt + String.valueOf(DELIMITER) + auctionId;
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * @throws IllegalArgumentException 커서 형식이나 값이 유효하지 않을 때
	 */
	public static ParticipatedAuctionHistoryCursor decode(String encoded) {
		try {
			String payload = new String(
				Base64.getUrlDecoder().decode(encoded),
				StandardCharsets.UTF_8
			);
			int delimiter = payload.lastIndexOf(DELIMITER);
			if (delimiter <= 0 || delimiter == payload.length() - 1) {
				throw new IllegalArgumentException(
					"participated auction cursor"
				);
			}
			return new ParticipatedAuctionHistoryCursor(
				Instant.parse(payload.substring(0, delimiter)),
				Long.parseLong(payload.substring(delimiter + 1))
			);
		} catch (DateTimeParseException | NumberFormatException exception) {
			throw new IllegalArgumentException(
				"participated auction cursor",
				exception
			);
		}
	}
}
