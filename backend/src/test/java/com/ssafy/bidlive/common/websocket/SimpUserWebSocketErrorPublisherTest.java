package com.ssafy.bidlive.common.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.security.Principal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** 사용자 개인 오류가 인증 Principal의 원래 세션으로만 발행되는지 검증합니다. */
@ExtendWith(MockitoExtension.class)
class SimpUserWebSocketErrorPublisherTest {

	private static final String PRINCIPAL_NAME = "25";
	private static final String SESSION_ID = "session-123";
	private static final String REF_DESTINATION = "/topic/auctions/100/questions";
	private static final String CORRELATION_ID = "receipt-123";

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	@Test
	void publishesErrorToAuthenticatedPrincipalRequestSession() {
		SimpUserWebSocketErrorPublisher publisher = publisher();
		StompErrorContext context = context(new TestPrincipal(PRINCIPAL_NAME));
		ArgumentCaptor<UserWebSocketErrorMessage> messageCaptor =
			ArgumentCaptor.forClass(UserWebSocketErrorMessage.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> headersCaptor =
			ArgumentCaptor.forClass(Map.class);

		publisher.publish(
			context,
			TestWebSocketErrorCode.SUBSCRIPTION_DENIED
		);

		verify(messagingTemplate).convertAndSendToUser(
			eq(PRINCIPAL_NAME),
			eq("/queue/errors"),
			messageCaptor.capture(),
			headersCaptor.capture()
		);

		UserWebSocketErrorMessage message = messageCaptor.getValue();
		assertThat(message.type()).isEqualTo("ERROR");
		assertThat(message.data().code()).isEqualTo("SUBSCRIPTION_DENIED");
		assertThat(message.data().message())
			.isEqualTo("해당 채널을 구독할 권한이 없습니다.");
		assertThat(message.data().refDestination()).isEqualTo(REF_DESTINATION);
		assertThat(message.data().correlationId()).isEqualTo(CORRELATION_ID);
		assertThat(headersCaptor.getValue())
			.containsEntry(SimpMessageHeaderAccessor.SESSION_ID_HEADER, SESSION_ID);
	}

	@Test
	void allowsErrorWithoutCorrelationId() {
		SimpUserWebSocketErrorPublisher publisher = publisher();
		StompErrorContext context = new StompErrorContext(
			new TestPrincipal(PRINCIPAL_NAME),
			SESSION_ID,
			REF_DESTINATION,
			null
		);
		ArgumentCaptor<UserWebSocketErrorMessage> messageCaptor =
			ArgumentCaptor.forClass(UserWebSocketErrorMessage.class);

		publisher.publish(
			context,
			TestWebSocketErrorCode.INTERNAL_ERROR
		);

		verify(messagingTemplate).convertAndSendToUser(
			eq(PRINCIPAL_NAME),
			eq("/queue/errors"),
			messageCaptor.capture(),
			org.mockito.ArgumentMatchers.anyMap()
		);
		assertThat(messageCaptor.getValue().data().correlationId()).isNull();
	}

	@Test
	void rejectsNullContextWithoutPublishing() {
		SimpUserWebSocketErrorPublisher publisher = publisher();

		assertThatThrownBy(() -> publisher.publish(
			null,
			TestWebSocketErrorCode.INTERNAL_ERROR
		))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("context");
		verifyNoInteractions(messagingTemplate);
	}

	@Test
	void rejectsNullErrorCodeWithoutPublishing() {
		SimpUserWebSocketErrorPublisher publisher = publisher();

		assertThatThrownBy(() -> publisher.publish(
			context(new TestPrincipal(PRINCIPAL_NAME)),
			null
		))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("errorCode");
		verifyNoInteractions(messagingTemplate);
	}

	@Test
	void rejectsBlankPrincipalNameWithoutPublishing() {
		SimpUserWebSocketErrorPublisher publisher = publisher();

		assertThatThrownBy(() -> publisher.publish(
			context(new TestPrincipal(" ")),
			TestWebSocketErrorCode.INTERNAL_ERROR
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Principal name은 비어 있을 수 없습니다.");
		verifyNoInteractions(messagingTemplate);
	}

	private SimpUserWebSocketErrorPublisher publisher() {
		return new SimpUserWebSocketErrorPublisher(messagingTemplate);
	}

	private StompErrorContext context(Principal principal) {
		return new StompErrorContext(
			principal,
			SESSION_ID,
			REF_DESTINATION,
			CORRELATION_ID
		);
	}

	private enum TestWebSocketErrorCode implements WebSocketErrorCode {

		SUBSCRIPTION_DENIED("해당 채널을 구독할 권한이 없습니다."),
		INTERNAL_ERROR("요청 처리에 실패했습니다.");

		private final String publicMessage;

		TestWebSocketErrorCode(String publicMessage) {
			this.publicMessage = publicMessage;
		}

		@Override
		public String code() {
			return name();
		}

		@Override
		public String publicMessage() {
			return publicMessage;
		}
	}

	private record TestPrincipal(String name) implements Principal {

		@Override
		public String getName() {
			return name;
		}
	}
}
