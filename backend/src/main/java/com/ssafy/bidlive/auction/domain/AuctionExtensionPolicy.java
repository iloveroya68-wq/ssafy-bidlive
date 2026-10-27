package com.ssafy.bidlive.auction.domain;

import java.time.Duration;
import java.time.Instant;

/** 판매자 권한·경매 상태·횟수·서버 기준 남은 시간으로 방송 연장 가능 여부를 판정한다. */
public final class AuctionExtensionPolicy {

	private static final int MAX_EXTENSION_COUNT = 3;
	private static final Duration MINIMUM_REMAINING_TIME =
		Duration.ofMinutes(1);

	private AuctionExtensionPolicy() {
	}

	/** 현재 연장 횟수에 대응하는 서버 정책 기준 잔여 연장 횟수를 반환합니다. */
	public static int remainingExtensions(int extensionCount) {
		if (extensionCount < 0 || extensionCount > MAX_EXTENSION_COUNT) {
			throw new IllegalArgumentException(
				"extensionCount must be between 0 and " + MAX_EXTENSION_COUNT
			);
		}
		return MAX_EXTENSION_COUNT - extensionCount;
	}

	/**
	 * 오류 우선순위는 권한 → LIVE 상태 → 횟수 → 남은 시간이다.
	 *
	 * <p>남은 시간이 정확히 1분이면 허용하고 그보다 짧을 때만 거절한다.
	 */
	public static AuctionExtensionDecision evaluate(
		AuctionExtensionState state,
		long requesterId,
		Instant serverTime
	) {
		if (state == null) {
			throw new IllegalArgumentException("state must not be null");
		}
		if (requesterId <= 0) {
			throw new IllegalArgumentException("requesterId must be positive");
		}
		if (serverTime == null) {
			throw new IllegalArgumentException("serverTime must not be null");
		}
		if (state.sellerId() != requesterId) {
			return AuctionExtensionDecision.rejected(
				AuctionExtensionErrorCode.NOT_AUCTION_SELLER
			);
		}
		if (state.status() != AuctionStatus.LIVE) {
			return AuctionExtensionDecision.rejected(
				AuctionExtensionErrorCode.AUCTION_NOT_LIVE
			);
		}
		if (state.extensionCount() >= MAX_EXTENSION_COUNT) {
			return AuctionExtensionDecision.rejected(
				AuctionExtensionErrorCode.EXTENSION_LIMIT_EXCEEDED
			);
		}

		Duration remainingTime = Duration.between(
			serverTime,
			state.endAt()
		);
		if (remainingTime.compareTo(MINIMUM_REMAINING_TIME) < 0) {
			return AuctionExtensionDecision.rejected(
				AuctionExtensionErrorCode.EXTENSION_TOO_LATE
			);
		}
		return AuctionExtensionDecision.allowed();
	}
}
