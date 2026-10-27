package com.ssafy.bidlive.media.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.bidlive.media.application.port.out.AuctionSnapshotVerification;
import org.junit.jupiter.api.Test;

/**
 * auction 검증 구현이 없는 동안 기본값은 업로드 차단이고, 설정을 명시적으로 켠 환경에서만
 * 통과하는 정책을 검증합니다.
 */
class PendingAuctionSnapshotVerifierTest {

	@Test
	void blocksSnapshotUploadByDefault() {
		LiveSnapshotProperties properties = new LiveSnapshotProperties();

		assertThat(properties.isSkipAuctionVerification()).isFalse();
		assertThat(new PendingAuctionSnapshotVerifier(properties).verify("room-123", 102L, 25L))
			.isEqualTo(AuctionSnapshotVerification.AUCTION_NOT_SETTLED);
	}

	@Test
	void allowsSnapshotUploadOnlyWhenVerificationIsExplicitlySkipped() {
		LiveSnapshotProperties properties = new LiveSnapshotProperties();
		properties.setSkipAuctionVerification(true);

		assertThat(new PendingAuctionSnapshotVerifier(properties).verify("room-123", 102L, 25L))
			.isEqualTo(AuctionSnapshotVerification.ALLOWED);
	}
}
