package com.ssafy.bidlive.auction.domain;

import java.util.Locale;

public enum AuctionStartType {
	IMMEDIATE,
	SCHEDULED;

	public static AuctionStartType from(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("startType is required");
		}
		return valueOf(value.trim().toUpperCase(Locale.ROOT));
	}
}
