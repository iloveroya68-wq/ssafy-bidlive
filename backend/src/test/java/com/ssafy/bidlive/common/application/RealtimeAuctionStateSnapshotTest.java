package com.ssafy.bidlive.common.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.ssafy.bidlive.common.application.RealtimeAuctionStateSnapshot.Status;
import org.junit.jupiter.api.Test;

/** Redis 스냅샷 불변 모델이 역행 가능한 음수 값을 거부하는지 검증합니다. */
class RealtimeAuctionStateSnapshotTest {

	@Test
	void rejectsNegativeStateVersion() {
		assertThatThrownBy(() -> new RealtimeAuctionStateSnapshot(
			-1L,
			Status.LIVE,
			Instant.parse("2026-08-02T12:10:00Z"),
			0,
			0L,
			0L
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("stateVersion");
	}
}
