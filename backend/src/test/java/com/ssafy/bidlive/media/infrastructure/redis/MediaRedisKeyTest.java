package com.ssafy.bidlive.media.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 미디어 Redis Key의 도메인·Hash Tag·용도 구성이 규칙과 일치하는지 검증합니다. */
class MediaRedisKeyTest {

	@Test
	void createsViewerSessionsKeyWithAuctionHashTag() {
		assertThat(MediaRedisKey.viewerSessions(42L))
			.isEqualTo("bidlive:media:{42}:viewer-sessions");
	}

	@Test
	void rejectsNonPositiveAuctionId() {
		assertThatThrownBy(() -> MediaRedisKey.viewerSessions(0L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("auctionId는 양수여야 합니다.");
	}
}
