package com.ssafy.bidlive.auction.application;

import java.time.Duration;
import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionExtensionErrorCode;

/** Redis 원자 연장의 성공 상태 또는 단일 거부 사유를 표현한다. */
public record AuctionExtensionProcessingResult(
	boolean accepted,
	AuctionExtensionErrorCode failureCode,
	Instant previousEndAt,
	Instant endAt,
	int extensionCount,
	int remainingExtensions,
	long version
) {

	private static final Duration EXTENSION_DURATION = Duration.ofMinutes(5);
	private static final int MAX_EXTENSION_COUNT = 3;

	public AuctionExtensionProcessingResult {
		if (accepted) {
			validateAccepted(
				failureCode,
				previousEndAt,
				endAt,
				extensionCount,
				remainingExtensions,
				version
			);
		} else {
			validateRejected(
				failureCode,
				previousEndAt,
				endAt,
				extensionCount,
				remainingExtensions,
				version
			);
		}
	}

	public static AuctionExtensionProcessingResult accepted(
		Instant previousEndAt,
		Instant endAt,
		int extensionCount,
		int remainingExtensions,
		long version
	) {
		return new AuctionExtensionProcessingResult(
			true,
			null,
			previousEndAt,
			endAt,
			extensionCount,
			remainingExtensions,
			version
		);
	}

	public static AuctionExtensionProcessingResult rejected(
		AuctionExtensionErrorCode failureCode
	) {
		return new AuctionExtensionProcessingResult(
			false,
			failureCode,
			null,
			null,
			0,
			0,
			0
		);
	}

	private static void validateAccepted(
		AuctionExtensionErrorCode failureCode,
		Instant previousEndAt,
		Instant endAt,
		int extensionCount,
		int remainingExtensions,
		long version
	) {
		if (failureCode != null || previousEndAt == null || endAt == null) {
			throw new IllegalArgumentException(
				"accepted result must have times and no failure code"
			);
		}
		if (!previousEndAt.plus(EXTENSION_DURATION).equals(endAt)) {
			throw new IllegalArgumentException(
				"accepted result must extend the deadline by five minutes"
			);
		}
		if (extensionCount < 1 || extensionCount > MAX_EXTENSION_COUNT
			|| remainingExtensions != MAX_EXTENSION_COUNT - extensionCount) {
			throw new IllegalArgumentException(
				"accepted result has invalid extension counts"
			);
		}
		if (version <= 0) {
			throw new IllegalArgumentException(
				"accepted result version must be positive"
			);
		}
	}

	private static void validateRejected(
		AuctionExtensionErrorCode failureCode,
		Instant previousEndAt,
		Instant endAt,
		int extensionCount,
		int remainingExtensions,
		long version
	) {
		if (failureCode == null) {
			throw new IllegalArgumentException(
				"rejected result must have a failure code"
			);
		}
		if (previousEndAt != null || endAt != null
			|| extensionCount != 0 || remainingExtensions != 0 || version != 0) {
			throw new IllegalArgumentException(
				"rejected result must not have an accepted state"
			);
		}
	}
}
