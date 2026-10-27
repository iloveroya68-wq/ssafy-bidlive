package com.ssafy.bidlive.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

/** STOMP Authorization native header의 단일 Bearer Token 추출 규칙을 검증합니다. */
class StompBearerTokenResolverTest {

	private static final String TOKEN = "header.payload.signature";

	private final StompBearerTokenResolver resolver = new StompBearerTokenResolver();

	@Test
	void resolvesSingleBearerToken() {
		StompHeaderAccessor accessor = connectAccessor();
		accessor.addNativeHeader("Authorization", "Bearer " + TOKEN);

		assertThat(resolver.resolve(accessor)).isEqualTo(TOKEN);
	}

	@Test
	void rejectsMissingAuthorizationHeader() {
		assertInvalid(connectAccessor());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"Basic credential",
		"bearer header.payload.signature",
		"Bearer",
		"Bearer ",
		"Bearer  header.payload.signature",
		"Bearer header.payload.signature "
	})
	void rejectsInvalidBearerFormat(String authorization) {
		StompHeaderAccessor accessor = connectAccessor();
		accessor.addNativeHeader("Authorization", authorization);

		assertInvalid(accessor);
	}

	@Test
	void rejectsDuplicateAuthorizationHeaders() {
		StompHeaderAccessor accessor = connectAccessor();
		accessor.addNativeHeader("Authorization", "Bearer " + TOKEN);
		accessor.addNativeHeader("Authorization", "Bearer another.token.value");

		assertInvalid(accessor);
	}

	private StompHeaderAccessor connectAccessor() {
		return StompHeaderAccessor.create(StompCommand.CONNECT);
	}

	private void assertInvalid(StompHeaderAccessor accessor) {
		assertThatThrownBy(() -> resolver.resolve(accessor))
			.isInstanceOfSatisfying(
				StompAuthenticationException.class,
				exception -> {
					assertThat(exception.errorCode())
						.isEqualTo(StompAuthenticationErrorCode.AUTH_INVALID);
					assertThat(exception.getMessage()).doesNotContain(TOKEN);
				}
			);
	}
}
