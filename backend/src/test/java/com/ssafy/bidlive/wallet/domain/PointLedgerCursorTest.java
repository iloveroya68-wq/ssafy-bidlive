package com.ssafy.bidlive.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class PointLedgerCursorTest {

	@Test
	void encodesAndDecodesAllOrderingKeys() {
		PointLedgerCursor cursor = new PointLedgerCursor(
			Instant.parse("2026-07-20T12:12:44Z"),
			1001L,
			WalletType.BETTING
		);

		assertThat(PointLedgerCursor.decode(cursor.encode()))
			.isEqualTo(cursor);
		assertThat(cursor.sourceOrder()).isEqualTo(1);
	}

	@Test
	void rejectsMalformedOrInvalidCursorValues() {
		assertThatThrownBy(() -> PointLedgerCursor.decode("not-a-cursor"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PointLedgerCursor(
			Instant.now(),
			0L,
			WalletType.TRADE
		)).isInstanceOf(IllegalArgumentException.class);
	}
}
