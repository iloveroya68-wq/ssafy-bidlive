package com.ssafy.bidlive.wallet.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/** 거래·베팅 원장을 함께 정렬할 때 사용하는 불투명한 키셋 커서다. */
public record PointLedgerCursor(
	Instant createdAt,
	long ledgerId,
	WalletType walletType
) {

	private static final char DELIMITER = '|';

	public PointLedgerCursor {
		if (createdAt == null || ledgerId <= 0 || walletType == null) {
			throw new IllegalArgumentException("point ledger cursor");
		}
	}

	public int sourceOrder() {
		return walletType == WalletType.TRADE ? 0 : 1;
	}

	public String encode() {
		String payload = createdAt
			+ String.valueOf(DELIMITER)
			+ ledgerId
			+ DELIMITER
			+ walletType.name();
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	public static PointLedgerCursor decode(String encoded) {
		try {
			String payload = new String(
				Base64.getUrlDecoder().decode(encoded),
				StandardCharsets.UTF_8
			);
			String[] parts = payload.split("\\|", -1);
			if (parts.length != 3) {
				throw new IllegalArgumentException("point ledger cursor");
			}
			return new PointLedgerCursor(
				Instant.parse(parts[0]),
				Long.parseLong(parts[1]),
				WalletType.valueOf(parts[2])
			);
		} catch (DateTimeParseException | IllegalArgumentException exception) {
			throw new IllegalArgumentException(
				"point ledger cursor",
				exception
			);
		}
	}
}
