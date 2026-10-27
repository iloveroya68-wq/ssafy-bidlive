package com.ssafy.bidlive.auction.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/** 판매자가 개최한 경매 이력의 최신순 키셋 페이징 커서다. */
public record HostedAuctionHistoryCursor(Instant sortedAt, long auctionId) {

	private static final char DELIMITER = '|';

	public HostedAuctionHistoryCursor {
		if (sortedAt == null || auctionId <= 0) {
			throw new IllegalArgumentException("hosted auction cursor");
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
	public static HostedAuctionHistoryCursor decode(String encoded) {
		try {
			String payload = new String(
				Base64.getUrlDecoder().decode(encoded),
				StandardCharsets.UTF_8
			);
			int delimiter = payload.lastIndexOf(DELIMITER);
			if (delimiter <= 0 || delimiter == payload.length() - 1) {
				throw new IllegalArgumentException("hosted auction cursor");
			}
			return new HostedAuctionHistoryCursor(
				Instant.parse(payload.substring(0, delimiter)),
				Long.parseLong(payload.substring(delimiter + 1))
			);
		} catch (DateTimeParseException | NumberFormatException exception) {
			throw new IllegalArgumentException("hosted auction cursor", exception);
		}
	}
}
