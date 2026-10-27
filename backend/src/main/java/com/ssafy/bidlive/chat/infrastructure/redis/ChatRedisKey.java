package com.ssafy.bidlive.chat.infrastructure.redis;

/** 채팅 도메인이 소유하는 Redis Key를 일관된 형식으로 생성한다. */
final class ChatRedisKey {

	private static final String SLOW_MODE_PATTERN =
		"bidlive:chat:{%d}:slow-mode:%d";
	private static final String MUTE_PATTERN =
		"bidlive:chat:{%d}:mute:%d";
	private static final String MODERATION_VIOLATIONS_PATTERN =
		"bidlive:chat:{%d}:moderation-violations:%d";

	private ChatRedisKey() {
	}

	static String slowMode(long auctionId, long memberId) {
		validateIdentifiers(auctionId, memberId);
		return SLOW_MODE_PATTERN.formatted(auctionId, memberId);
	}

	static String mute(long auctionId, long memberId) {
		validateIdentifiers(auctionId, memberId);
		return MUTE_PATTERN.formatted(auctionId, memberId);
	}

	static String moderationViolations(long auctionId, long memberId) {
		validateIdentifiers(auctionId, memberId);
		return MODERATION_VIOLATIONS_PATTERN.formatted(auctionId, memberId);
	}

	private static void validateIdentifiers(
		long auctionId,
		long memberId
	) {
		if (auctionId <= 0) {
			throw new IllegalArgumentException(
				"auctionId must be positive"
			);
		}
		if (memberId <= 0) {
			throw new IllegalArgumentException(
				"memberId must be positive"
			);
		}
	}
}
