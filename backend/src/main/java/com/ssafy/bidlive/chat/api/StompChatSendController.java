package com.ssafy.bidlive.chat.api;

import java.security.Principal;
import java.time.Duration;

import com.ssafy.bidlive.chat.api.dto.ChatSendRequest;
import com.ssafy.bidlive.chat.application.ChatSendCommand;
import com.ssafy.bidlive.chat.application.ChatSendException;
import com.ssafy.bidlive.chat.application.ChatSendService;
import com.ssafy.bidlive.chat.application.ChatSendResult;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics.ChatSendOutcome;
import com.ssafy.bidlive.common.security.SecurityPrincipal;
import com.ssafy.bidlive.common.websocket.StompErrorContext;
import com.ssafy.bidlive.common.websocket.StompErrorContextResolver;
import com.ssafy.bidlive.common.websocket.UserWebSocketErrorPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 인증된 STOMP 채팅 요청을 유스케이스로 전달하고 정책 실패를 개인 오류로 변환한다. */
@Controller
public class StompChatSendController {
	private static final Logger log = LoggerFactory.getLogger(
		StompChatSendController.class
	);

	private final ChatSendService chatSendService;
	private final RealtimeObservationMetrics metrics;
	private final StompErrorContextResolver errorContextResolver;
	private final UserWebSocketErrorPublisher errorPublisher;

	public StompChatSendController(
		ChatSendService chatSendService,
		RealtimeObservationMetrics metrics,
		StompErrorContextResolver errorContextResolver,
		UserWebSocketErrorPublisher errorPublisher
	) {
		this.chatSendService = chatSendService;
		this.metrics = metrics;
		this.errorContextResolver = errorContextResolver;
		this.errorPublisher = errorPublisher;
	}

	@MessageMapping("/auctions/{auctionId}/chat")
	public void send(
		@DestinationVariable("auctionId") long auctionId,
		@Payload(required = false) ChatSendRequest request,
		Message<?> message
	) {
		String correlationId = request == null
			? null
			: request.clientMessageId();
		StompErrorContext errorContext = errorContextResolver.resolve(
			message,
			correlationId
		);
		long startedAt = System.nanoTime();
		ChatSendOutcome outcome = ChatSendOutcome.FAILED;

		try {
			SecurityPrincipal principal = requireSecurityPrincipal(
				errorContext.principal()
			);
			ChatSendResult result = chatSendService.send(
				toCommand(auctionId, principal, request)
			);
			outcome = result != null && result.created()
				? ChatSendOutcome.CREATED
				: ChatSendOutcome.DEDUPLICATED;
			log.atInfo()
				.addKeyValue("auctionId", auctionId)
				.addKeyValue("clientMessageId", correlationId)
				.addKeyValue(
					"messageId",
					result == null ? null : result.messageId().value()
				)
				.addKeyValue("eventType", "CHAT_SEND")
				.addKeyValue("outcome", outcome.name())
				.log("Chat send processed");
		} catch (ChatSendException exception) {
			outcome = ChatSendOutcome.REJECTED;
			log.atWarn()
				.addKeyValue("auctionId", auctionId)
				.addKeyValue("clientMessageId", correlationId)
				.addKeyValue("eventType", "CHAT_SEND")
				.addKeyValue("outcome", outcome.name())
				.addKeyValue("errorCode", exception.errorCode().name())
				.log("Chat send rejected");
			errorPublisher.publish(errorContext, exception.errorCode());
		} catch (RuntimeException exception) {
			log.atError()
				.addKeyValue("auctionId", auctionId)
				.addKeyValue("clientMessageId", correlationId)
				.addKeyValue("eventType", "CHAT_SEND")
				.addKeyValue("outcome", outcome.name())
				.addKeyValue(
					"failureType",
					exception.getClass().getSimpleName()
				)
				.log("Chat send failed");
			throw exception;
		} finally {
			metrics.recordChatSend(
				Duration.ofNanos(System.nanoTime() - startedAt),
				outcome
			);
		}
	}

	private ChatSendCommand toCommand(
		long auctionId,
		SecurityPrincipal principal,
		ChatSendRequest request
	) {
		if (request == null) {
			return new ChatSendCommand(
				auctionId,
				principal.memberId(),
				null,
				null,
				false
			);
		}

		return new ChatSendCommand(
			auctionId,
			principal.memberId(),
			request.clientMessageId(),
			request.content(),
			request.isQuestion()
		);
	}

	private SecurityPrincipal requireSecurityPrincipal(Principal principal) {
		if (principal instanceof SecurityPrincipal securityPrincipal) {
			return securityPrincipal;
		}

		throw new IllegalStateException(
			"authenticated STOMP SecurityPrincipal is required"
		);
	}
}
