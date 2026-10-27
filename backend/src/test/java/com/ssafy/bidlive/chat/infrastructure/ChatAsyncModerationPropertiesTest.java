package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 비동기 검열 활성화 시 필수 외부 설정 검증을 확인합니다. */
class ChatAsyncModerationPropertiesTest {

	@Test
	void disabledConfigurationDoesNotRequireSecret() {
		assertThatCode(
			new ChatAsyncModerationProperties()::validateEnabled
		).doesNotThrowAnyException();
	}

	@Test
	void enabledConfigurationRequiresEndpointAndSecret() {
		ChatAsyncModerationProperties properties =
			new ChatAsyncModerationProperties();
		properties.setEnabled(true);

		assertThatThrownBy(properties::validateEnabled)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("base-url");
	}
}
