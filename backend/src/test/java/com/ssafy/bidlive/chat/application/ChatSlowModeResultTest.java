package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 슬로우 모드 허용·제한 결과가 일관된 대기 시간을 보장하는지 검증한다. */
class ChatSlowModeResultTest {

	@Test
	void createsPermittedResultWithZeroRetryDelay() {
		ChatSlowModeResult result = ChatSlowModeResult.permit();

		assertThat(result.allowed()).isTrue();
		assertThat(result.retryAfterSeconds()).isZero();
	}

	@Test
	void createsLimitedResultWithPositiveRetryDelay() {
		ChatSlowModeResult result = ChatSlowModeResult.limited(3);

		assertThat(result.allowed()).isFalse();
		assertThat(result.retryAfterSeconds()).isEqualTo(3);
	}

	@Test
	void rejectsInconsistentResults() {
		assertThatThrownBy(() -> new ChatSlowModeResult(true, 1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ChatSlowModeResult(false, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ChatSlowModeResult(false, -1))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
