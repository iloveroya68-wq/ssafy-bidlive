package com.ssafy.bidlive.auction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** 판매자 방송 연장의 권한·상태·횟수·남은 시간 경계와 판정 순서를 검증한다. */
class AuctionExtensionPolicyTest {

	private static final long SELLER_ID = 25L;
	private static final Instant NOW =
		Instant.parse("2026-07-20T12:20:00Z");

	@Test
	void calculatesRemainingExtensionsFromServerPolicy() {
		assertThat(AuctionExtensionPolicy.remainingExtensions(0)).isEqualTo(3);
		assertThat(AuctionExtensionPolicy.remainingExtensions(1)).isEqualTo(2);
		assertThat(AuctionExtensionPolicy.remainingExtensions(3)).isZero();
	}

	@Test
	void rejectsExtensionCountOutsideServerPolicy() {
		assertThatThrownBy(() -> AuctionExtensionPolicy.remainingExtensions(-1))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("extensionCount");
		assertThatThrownBy(() -> AuctionExtensionPolicy.remainingExtensions(4))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("extensionCount");
	}

	@Test
	void allowsSellerAtExactlyOneMinuteWithOneExtensionRemaining() {
		AuctionExtensionDecision decision = AuctionExtensionPolicy.evaluate(
			state(AuctionStatus.LIVE, NOW.plusSeconds(60), 2),
			SELLER_ID,
			NOW
		);

		assertThat(decision.extendable()).isTrue();
		assertThat(decision.failureCode()).isNull();
	}

	@Test
	void rejectsNonSellerBeforeOtherConditions() {
		AuctionExtensionDecision decision = AuctionExtensionPolicy.evaluate(
			state(AuctionStatus.ENDED, NOW.minusSeconds(1), 3),
			999L,
			NOW
		);

		assertRejected(
			decision,
			AuctionExtensionErrorCode.NOT_AUCTION_SELLER
		);
	}

	@Test
	void rejectsAuctionThatIsNotLive() {
		for (AuctionStatus status : new AuctionStatus[] {
			AuctionStatus.WAITING,
			AuctionStatus.ENDED
		}) {
			AuctionExtensionDecision decision =
				AuctionExtensionPolicy.evaluate(
					state(status, NOW.plusSeconds(60), 0),
					SELLER_ID,
					NOW
				);

			assertRejected(
				decision,
				AuctionExtensionErrorCode.AUCTION_NOT_LIVE
			);
		}
	}

	@Test
	void rejectsThirdCompletedExtensionBeforeTimeCondition() {
		AuctionExtensionDecision decision = AuctionExtensionPolicy.evaluate(
			state(AuctionStatus.LIVE, NOW.minusSeconds(1), 3),
			SELLER_ID,
			NOW
		);

		assertRejected(
			decision,
			AuctionExtensionErrorCode.EXTENSION_LIMIT_EXCEEDED
		);
	}

	@Test
	void rejectsImmediatelyBelowOneMinute() {
		AuctionExtensionDecision decision = AuctionExtensionPolicy.evaluate(
			state(AuctionStatus.LIVE, NOW.plusMillis(59_999), 0),
			SELLER_ID,
			NOW
		);

		assertRejected(
			decision,
			AuctionExtensionErrorCode.EXTENSION_TOO_LATE
		);
	}

	@Test
	void rejectsExpiredAuctionAsTooLateWhenRedisStatusIsStillLive() {
		AuctionExtensionDecision decision = AuctionExtensionPolicy.evaluate(
			state(AuctionStatus.LIVE, NOW.minusSeconds(1), 0),
			SELLER_ID,
			NOW
		);

		assertRejected(
			decision,
			AuctionExtensionErrorCode.EXTENSION_TOO_LATE
		);
	}

	@Test
	void rejectsInvalidPolicyInputs() {
		assertThatThrownBy(() ->
			AuctionExtensionPolicy.evaluate(null, SELLER_ID, NOW)
		).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
			AuctionExtensionPolicy.evaluate(
				state(AuctionStatus.LIVE, NOW.plusSeconds(60), 0),
				0,
				NOW
			)
		).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
			AuctionExtensionPolicy.evaluate(
				state(AuctionStatus.LIVE, NOW.plusSeconds(60), 0),
				SELLER_ID,
				null
			)
		).isInstanceOf(IllegalArgumentException.class);
	}

	private static AuctionExtensionState state(
		AuctionStatus status,
		Instant endAt,
		int extensionCount
	) {
		return new AuctionExtensionState(
			SELLER_ID,
			status,
			endAt,
			extensionCount
		);
	}

	private static void assertRejected(
		AuctionExtensionDecision decision,
		AuctionExtensionErrorCode failureCode
	) {
		assertThat(decision.extendable()).isFalse();
		assertThat(decision.failureCode()).isEqualTo(failureCode);
	}
}
