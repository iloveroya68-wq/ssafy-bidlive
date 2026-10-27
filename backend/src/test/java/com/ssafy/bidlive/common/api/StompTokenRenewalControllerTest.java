package com.ssafy.bidlive.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.ssafy.bidlive.common.api.dto.StompTokenRenewalRequest;
import com.ssafy.bidlive.common.security.SecurityPrincipal;
import com.ssafy.bidlive.common.security.StompTokenRenewalErrorCode;
import com.ssafy.bidlive.common.security.StompTokenRenewalException;
import com.ssafy.bidlive.common.security.StompTokenRenewalResult;
import com.ssafy.bidlive.common.security.StompTokenRenewalService;
import com.ssafy.bidlive.common.websocket.StompErrorContext;
import com.ssafy.bidlive.common.websocket.StompErrorContextResolver;
import com.ssafy.bidlive.common.websocket.StompReceiptPublisher;
import com.ssafy.bidlive.common.websocket.StompSessionTokenExpirationUpdater;
import com.ssafy.bidlive.common.websocket.UserWebSocketErrorPublisher;
import com.ssafy.bidlive.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/** STOMP 재인증 진입점의 성공 갱신과 개인 오류 전환 흐름을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class StompTokenRenewalControllerTest {

	private static final String TOKEN = "sensitive-renewal-access-token";
	private static final String SESSION_ID = "session-251";
	private static final String DESTINATION = "/app/auth/renew";
	private static final String RECEIPT_ID = "renew-receipt-1";
	private static final Instant EXPIRES_AT = Instant.parse("2026-07-29T12:00:00Z");

	@Mock
	private StompTokenRenewalService renewalService;

	@Mock
	private StompSessionTokenExpirationUpdater expirationUpdater;

	@Mock
	private StompErrorContextResolver errorContextResolver;

	@Mock
	private UserWebSocketErrorPublisher errorPublisher;

	@Mock
	private StompReceiptPublisher receiptPublisher;

	private StompTokenRenewalController controller;
	private SecurityPrincipal principal;

	@BeforeEach
	void setUp() {
		controller = new StompTokenRenewalController(
			renewalService,
			expirationUpdater,
			errorContextResolver,
			errorPublisher,
			receiptPublisher
		);
		principal = new SecurityPrincipal(25L, MemberRole.USER);
	}

	@Test
	void renewsSessionExpirationWithoutReplacingConnectionState() {
		Map<String, Object> sessionAttributes = new HashMap<>();
		Message<byte[]> message = message(principal, sessionAttributes);
		StompErrorContext errorContext = errorContext(principal);
		when(errorContextResolver.resolve(message)).thenReturn(errorContext);
		when(renewalService.renew(principal, TOKEN)).thenReturn(
			new StompTokenRenewalResult(EXPIRES_AT)
		);

		controller.renew(new StompTokenRenewalRequest(TOKEN), message);

		verify(expirationUpdater).update(sessionAttributes, EXPIRES_AT);
		verify(receiptPublisher).publish(SESSION_ID, RECEIPT_ID);
		verifyNoInteractions(errorPublisher);
	}

	@ParameterizedTest
	@EnumSource(StompTokenRenewalErrorCode.class)
	void publishesPersonalErrorWithoutUpdatingSession(
		StompTokenRenewalErrorCode errorCode
	) {
		Map<String, Object> sessionAttributes = new HashMap<>();
		Message<byte[]> message = message(principal, sessionAttributes);
		StompErrorContext errorContext = errorContext(principal);
		when(errorContextResolver.resolve(message)).thenReturn(errorContext);
		when(renewalService.renew(principal, TOKEN)).thenThrow(
			new StompTokenRenewalException(errorCode)
		);

		controller.renew(new StompTokenRenewalRequest(TOKEN), message);

		verify(errorPublisher).publish(errorContext, errorCode);
		verifyNoInteractions(expirationUpdater, receiptPublisher);
	}

	@Test
	void publishesPersonalErrorWhenSessionExpirationUpdateIsRejected() {
		Map<String, Object> sessionAttributes = new HashMap<>();
		Message<byte[]> message = message(principal, sessionAttributes);
		StompErrorContext errorContext = errorContext(principal);
		when(errorContextResolver.resolve(message)).thenReturn(errorContext);
		when(renewalService.renew(principal, TOKEN)).thenReturn(
			new StompTokenRenewalResult(EXPIRES_AT)
		);
		when(expirationUpdater.update(sessionAttributes, EXPIRES_AT)).thenThrow(
			new StompTokenRenewalException(
				StompTokenRenewalErrorCode.AUTH_RENEW_INVALID
			)
		);

		controller.renew(new StompTokenRenewalRequest(TOKEN), message);

		verify(errorPublisher).publish(
			errorContext,
			StompTokenRenewalErrorCode.AUTH_RENEW_INVALID
		);
		verifyNoInteractions(receiptPublisher);
	}

	@Test
	void rejectsNonSecurityPrincipalThroughPersonalError() {
		Principal unsupportedPrincipal = () -> "25";
		Map<String, Object> sessionAttributes = new HashMap<>();
		Message<byte[]> message = message(unsupportedPrincipal, sessionAttributes);
		StompErrorContext errorContext = errorContext(unsupportedPrincipal);
		when(errorContextResolver.resolve(message)).thenReturn(errorContext);

		controller.renew(new StompTokenRenewalRequest(TOKEN), message);

		verify(errorPublisher).publish(
			errorContext,
			StompTokenRenewalErrorCode.AUTH_RENEW_INVALID
		);
		verifyNoInteractions(renewalService, expirationUpdater, receiptPublisher);
	}

	@Test
	void rejectsMissingSessionAttributesThroughPersonalError() {
		Message<byte[]> message = messageWithoutSessionAttributes(principal);
		StompErrorContext errorContext = errorContext(principal);
		when(errorContextResolver.resolve(message)).thenReturn(errorContext);

		controller.renew(new StompTokenRenewalRequest(TOKEN), message);

		verify(errorPublisher).publish(
			errorContext,
			StompTokenRenewalErrorCode.AUTH_RENEW_INVALID
		);
		verifyNoInteractions(renewalService, expirationUpdater, receiptPublisher);
	}

	@Test
	void passesMissingAccessTokenToVerificationPolicy() {
		Map<String, Object> sessionAttributes = new HashMap<>();
		Message<byte[]> message = message(principal, sessionAttributes);
		StompErrorContext errorContext = errorContext(principal);
		when(errorContextResolver.resolve(message)).thenReturn(errorContext);
		when(renewalService.renew(principal, null)).thenThrow(
			new StompTokenRenewalException(
				StompTokenRenewalErrorCode.AUTH_RENEW_INVALID
			)
		);

		controller.renew(new StompTokenRenewalRequest(null), message);

		verify(errorPublisher).publish(
			errorContext,
			StompTokenRenewalErrorCode.AUTH_RENEW_INVALID
		);
		verifyNoInteractions(expirationUpdater, receiptPublisher);
	}

	@Test
	void redactsAccessTokenFromRequestString() {
		StompTokenRenewalRequest request = new StompTokenRenewalRequest(TOKEN);

		assertThat(request.toString())
			.doesNotContain(TOKEN)
			.contains("[REDACTED]");
	}

	private Message<byte[]> message(
		Principal messagePrincipal,
		Map<String, Object> sessionAttributes
	) {
		StompHeaderAccessor accessor = accessor(messagePrincipal);
		accessor.setSessionAttributes(sessionAttributes);
		return buildMessage(accessor);
	}

	private Message<byte[]> messageWithoutSessionAttributes(
		Principal messagePrincipal
	) {
		return buildMessage(accessor(messagePrincipal));
	}

	private StompHeaderAccessor accessor(Principal messagePrincipal) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setUser(messagePrincipal);
		accessor.setSessionId(SESSION_ID);
		accessor.setDestination(DESTINATION);
		accessor.setReceipt(RECEIPT_ID);
		accessor.setLeaveMutable(true);
		return accessor;
	}

	private Message<byte[]> buildMessage(StompHeaderAccessor accessor) {
		return MessageBuilder.createMessage(
			"{}".getBytes(StandardCharsets.UTF_8),
			accessor.getMessageHeaders()
		);
	}

	private StompErrorContext errorContext(Principal contextPrincipal) {
		return new StompErrorContext(
			contextPrincipal,
			SESSION_ID,
			DESTINATION,
			RECEIPT_ID
		);
	}
}
