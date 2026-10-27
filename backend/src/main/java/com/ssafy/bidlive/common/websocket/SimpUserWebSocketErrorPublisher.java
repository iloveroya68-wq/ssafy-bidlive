package com.ssafy.bidlive.common.websocket;

import java.util.Objects;

import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Spring 사용자 destination을 이용해 오류를 원래 요청 세션에만 전송합니다. */
@Component
public class SimpUserWebSocketErrorPublisher implements UserWebSocketErrorPublisher {

	private static final String ERROR_DESTINATION = "/queue/errors";

	private final SimpMessagingTemplate messagingTemplate;

	public SimpUserWebSocketErrorPublisher(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	public void publish(
		StompErrorContext context,
		WebSocketErrorCode errorCode
	) {
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(errorCode, "errorCode");

		String principalName = requirePrincipalName(context);
		UserWebSocketErrorMessage errorMessage = UserWebSocketErrorMessage.error(
			errorCode,
			context.refDestination(),
			context.correlationId()
		);
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(
			SimpMessageType.MESSAGE
		);
		headers.setSessionId(context.sessionId());
		headers.setLeaveMutable(true);

		messagingTemplate.convertAndSendToUser(
			principalName,
			ERROR_DESTINATION,
			errorMessage,
			headers.getMessageHeaders()
		);
	}

	private String requirePrincipalName(StompErrorContext context) {
		String principalName = context.principal().getName();
		if (principalName == null || principalName.isBlank()) {
			throw new IllegalArgumentException(
				"Principal name은 비어 있을 수 없습니다."
			);
		}
		return principalName;
	}
}
