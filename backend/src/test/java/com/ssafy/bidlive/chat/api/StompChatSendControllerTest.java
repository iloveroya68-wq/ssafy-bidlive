package com.ssafy.bidlive.chat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.security.Principal;
import java.time.Duration;

import com.ssafy.bidlive.chat.api.dto.ChatSendRequest;
import com.ssafy.bidlive.chat.application.ChatSendCommand;
import com.ssafy.bidlive.chat.application.ChatSendErrorCode;
import com.ssafy.bidlive.chat.application.ChatSendException;
import com.ssafy.bidlive.chat.application.ChatSendService;
import com.ssafy.bidlive.chat.application.ChatSendResult;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics.ChatSendOutcome;
import com.ssafy.bidlive.common.security.SecurityPrincipal;
import com.ssafy.bidlive.common.websocket.StompErrorContext;
import com.ssafy.bidlive.common.websocket.StompErrorContextResolver;
import com.ssafy.bidlive.common.websocket.UserWebSocketErrorPublisher;
import com.ssafy.bidlive.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;

/** STOMP 채팅 진입점의 Principal 변환과 개인 오류 발행을 검증한다. */
@ExtendWith(MockitoExtension.class)
class StompChatSendControllerTest {

	private static final long AUCTION_ID = 100L;
	private static final long MEMBER_ID = 25L;
	private static final String CLIENT_MESSAGE_ID =
		"018f6f4e-2a61-7c00-a1b2-334455667788";
	private static final String CONTENT = "키캡 상태 어떤가요?";
	private static final String SESSION_ID = "session-539";
	private static final String DESTINATION = "/app/auctions/100/chat";

	@Mock
	private ChatSendService chatSendService;

	@Mock
	private StompErrorContextResolver errorContextResolver;

	@Mock
	private UserWebSocketErrorPublisher errorPublisher;

	@Mock
	private RealtimeObservationMetrics metrics;

	@Mock
	private Message<byte[]> message;

	private StompChatSendController controller;
	private SecurityPrincipal principal;
	private StompErrorContext errorContext;

	@BeforeEach
	void setUp() {
		controller = new StompChatSendController(
			chatSendService,
			metrics,
			errorContextResolver,
			errorPublisher
		);
		principal = new SecurityPrincipal(MEMBER_ID, MemberRole.USER);
		errorContext = new StompErrorContext(
			principal,
			SESSION_ID,
			DESTINATION,
			CLIENT_MESSAGE_ID
		);
	}

	@Test
	void forwardsAuthenticatedMemberAndPayloadToChatSendService() {
		ChatSendRequest request = new ChatSendRequest(
			CLIENT_MESSAGE_ID,
			CONTENT,
			true
		);
		when(errorContextResolver.resolve(
			message,
			CLIENT_MESSAGE_ID
		)).thenReturn(errorContext);
		when(chatSendService.send(any(ChatSendCommand.class))).thenReturn(
			ChatSendResult.created(
				new ChatMessageId("01J8W3P2N9KQ4T123456789ABC")
			)
		);

		controller.send(AUCTION_ID, request, message);

		ArgumentCaptor<ChatSendCommand> commandCaptor =
			ArgumentCaptor.forClass(ChatSendCommand.class);
		verify(chatSendService).send(commandCaptor.capture());
		assertThat(commandCaptor.getValue()).isEqualTo(
			new ChatSendCommand(
				AUCTION_ID,
				MEMBER_ID,
				CLIENT_MESSAGE_ID,
				CONTENT,
				true
			)
		);
		verifyNoInteractions(errorPublisher);
		verify(metrics).recordChatSend(
			any(Duration.class),
			eq(ChatSendOutcome.CREATED)
		);
	}

	@ParameterizedTest
	@EnumSource(ChatSendErrorCode.class)
	void publishesChatPolicyFailureToRequestSession(
		ChatSendErrorCode errorCode
	) {
		ChatSendRequest request = new ChatSendRequest(
			CLIENT_MESSAGE_ID,
			CONTENT,
			false
		);
		when(errorContextResolver.resolve(
			message,
			CLIENT_MESSAGE_ID
		)).thenReturn(errorContext);
		when(chatSendService.send(any(ChatSendCommand.class))).thenThrow(
			new ChatSendException(errorCode)
		);

		controller.send(AUCTION_ID, request, message);

		verify(errorPublisher).publish(errorContext, errorCode);
		verify(metrics).recordChatSend(
			any(Duration.class),
			eq(ChatSendOutcome.REJECTED)
		);
	}

	@Test
	void forwardsMissingPayloadForContentPolicyRejection() {
		StompErrorContext contextWithoutCorrelation =
			new StompErrorContext(
				principal,
				SESSION_ID,
				DESTINATION,
				null
			);
		when(errorContextResolver.resolve(message, null))
			.thenReturn(contextWithoutCorrelation);

		controller.send(AUCTION_ID, null, message);

		ArgumentCaptor<ChatSendCommand> commandCaptor =
			ArgumentCaptor.forClass(ChatSendCommand.class);
		verify(chatSendService).send(commandCaptor.capture());
		ChatSendCommand command = commandCaptor.getValue();
		assertThat(command.auctionId()).isEqualTo(AUCTION_ID);
		assertThat(command.memberId()).isEqualTo(MEMBER_ID);
		assertThat(command.clientMessageId()).isNull();
		assertThat(command.content()).isNull();
		assertThat(command.isQuestion()).isFalse();
	}

	@Test
	void rejectsPrincipalThatWasNotCreatedByStompAuthentication() {
		Principal unsupportedPrincipal = () -> Long.toString(MEMBER_ID);
		StompErrorContext unsupportedContext = new StompErrorContext(
			unsupportedPrincipal,
			SESSION_ID,
			DESTINATION,
			CLIENT_MESSAGE_ID
		);
		when(errorContextResolver.resolve(
			message,
			CLIENT_MESSAGE_ID
		)).thenReturn(unsupportedContext);

		assertThatThrownBy(() -> controller.send(
			AUCTION_ID,
			new ChatSendRequest(CLIENT_MESSAGE_ID, CONTENT, false),
			message
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage(
				"authenticated STOMP SecurityPrincipal is required"
			);

		verifyNoInteractions(chatSendService, errorPublisher);
	}

	@Test
	void mapsApplicationDestinationWithoutAppPrefix() throws Exception {
		Method sendMethod = StompChatSendController.class.getMethod(
			"send",
			long.class,
			ChatSendRequest.class,
			Message.class
		);

		MessageMapping mapping =
			sendMethod.getAnnotation(MessageMapping.class);

		assertThat(mapping).isNotNull();
		assertThat(mapping.value())
			.containsExactly("/auctions/{auctionId}/chat");
	}
}
