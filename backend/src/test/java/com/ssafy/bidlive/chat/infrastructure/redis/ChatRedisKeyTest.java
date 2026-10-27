package com.ssafy.bidlive.chat.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 슬로우 모드 Redis Key의 도메인·Hash Tag·회원 격리 형식을 검증한다. */
class ChatRedisKeyTest {

	@Test
	void createsAuctionHashTaggedSlowModeKey() {
		assertThat(ChatRedisKey.slowMode(100L, 25L))
			.isEqualTo("bidlive:chat:{100}:slow-mode:25");
	}

	@Test
	void createsAuctionHashTaggedMuteKey() {
		assertThat(ChatRedisKey.mute(100L, 25L))
			.isEqualTo("bidlive:chat:{100}:mute:25");
	}

	@Test
	void createsAuctionHashTaggedModerationViolationKey() {
		assertThat(ChatRedisKey.moderationViolations(100L, 25L))
			.isEqualTo("bidlive:chat:{100}:moderation-violations:25");
	}

	@Test
	void isolatesSlowModeAndMuteKeysByAuctionAndMember() {
		assertThat(ChatRedisKey.slowMode(100L, 25L))
			.isNotEqualTo(ChatRedisKey.slowMode(100L, 26L))
			.isNotEqualTo(ChatRedisKey.slowMode(101L, 25L))
			.isNotEqualTo(ChatRedisKey.mute(100L, 25L));
		assertThat(ChatRedisKey.mute(100L, 25L))
			.isNotEqualTo(ChatRedisKey.mute(100L, 26L))
			.isNotEqualTo(ChatRedisKey.mute(101L, 25L));
	}

	@Test
	void rejectsNonPositiveIdentifiers() {
		assertThatThrownBy(() -> ChatRedisKey.slowMode(0L, 25L))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ChatRedisKey.slowMode(100L, 0L))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ChatRedisKey.mute(0L, 25L))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ChatRedisKey.mute(100L, 0L))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
