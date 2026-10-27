package com.ssafy.bidlive.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.bidlive.common.websocket.UserWebSocketErrorMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** STOMP 토큰 재인증 오류 코드의 공개 형식과 예외의 민감정보 비노출을 검증합니다. */
class StompTokenRenewalExceptionTest {

	private static final String REF_DESTINATION = "/app/auth/renew";
	private static final String CORRELATION_ID = "renew-receipt-1";

	@ParameterizedTest
	@EnumSource(StompTokenRenewalErrorCode.class)
	void exposesStableCode(StompTokenRenewalErrorCode errorCode) {
		assertThat(errorCode.code()).isEqualTo(errorCode.name());
	}

	@Test
	void exposesSafeFixedPublicMessages() {
		assertThat(StompTokenRenewalErrorCode.AUTH_RENEW_INVALID.publicMessage())
			.isEqualTo("재인증에 사용할 Access Token이 유효하지 않습니다.");
		assertThat(StompTokenRenewalErrorCode.AUTH_PRINCIPAL_MISMATCH.publicMessage())
			.isEqualTo("현재 연결의 사용자와 Access Token의 사용자가 일치하지 않습니다.");
		assertThat(StompTokenRenewalErrorCode.AUTH_RENEW_EXPIRED.publicMessage())
			.isEqualTo("재인증에 사용할 Access Token이 만료되었습니다.");
	}

	@ParameterizedTest
	@EnumSource(StompTokenRenewalErrorCode.class)
	void carriesOnlyPublicErrorInformation(StompTokenRenewalErrorCode errorCode) {
		StompTokenRenewalException exception = new StompTokenRenewalException(errorCode);

		assertThat(exception.errorCode()).isEqualTo(errorCode);
		assertThat(exception.getMessage()).isEqualTo(errorCode.publicMessage());
	}

	@Test
	void convertsToPersonalWebSocketErrorContract() {
		UserWebSocketErrorMessage message = UserWebSocketErrorMessage.error(
			StompTokenRenewalErrorCode.AUTH_PRINCIPAL_MISMATCH,
			REF_DESTINATION,
			CORRELATION_ID
		);

		assertThat(message.type()).isEqualTo("ERROR");
		assertThat(message.data().code()).isEqualTo("AUTH_PRINCIPAL_MISMATCH");
		assertThat(message.data().message()).isEqualTo(
			"현재 연결의 사용자와 Access Token의 사용자가 일치하지 않습니다."
		);
		assertThat(message.data().refDestination()).isEqualTo(REF_DESTINATION);
		assertThat(message.data().correlationId()).isEqualTo(CORRELATION_ID);
	}

	@Test
	void rejectsMissingErrorCode() {
		assertThatThrownBy(() -> new StompTokenRenewalException(null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("errorCode");
	}
}
