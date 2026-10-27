package com.ssafy.bidlive.auction.infrastructure.redis;

/** AI-03가 소유하는 Redis key를 경매 단위 hash-tag로 묶는다. */
final class CatchUpRedisKey {

	private static final String PREFIX = "bidlive:auction:{%d}:catch-up:";

	private CatchUpRedisKey() {
	}

	static String state(long auctionId) {
		return prefix(auctionId) + "state";
	}

	static String transcripts(long auctionId) {
		return prefix(auctionId) + "transcripts";
	}

	static String summary(long auctionId) {
		return prefix(auctionId) + "summary";
	}

	static String transcriptRevision(long auctionId) {
		return prefix(auctionId) + "transcript-revision";
	}

	static String summaryRevision(long auctionId) {
		return prefix(auctionId) + "summary-revision";
	}

	static String summaryRefreshLock(long auctionId) {
		return prefix(auctionId) + "summary-refresh-lock";
	}

	static String audioClaim(long auctionId, long sequence) {
		if (sequence < 0) {
			throw new IllegalArgumentException("sequence must not be negative");
		}
		return prefix(auctionId) + "audio-claim:" + sequence;
	}

	private static String prefix(long auctionId) {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		return PREFIX.formatted(auctionId);
	}
}
