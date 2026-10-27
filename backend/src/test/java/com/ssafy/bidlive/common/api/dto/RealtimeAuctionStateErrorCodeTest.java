package com.ssafy.bidlive.common.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** WS-03 실패 코드가 확정된 두 값으로 유지되는지 검증합니다. */
class RealtimeAuctionStateErrorCodeTest {

	@Test
	void exposesOnlyConfirmedErrorCodes() {
		assertThat(RealtimeAuctionStateErrorCode.values())
			.containsExactly(
				RealtimeAuctionStateErrorCode.AUCTION_NOT_FOUND,
				RealtimeAuctionStateErrorCode.AUCTION_STATE_UNAVAILABLE
			);
	}
}
