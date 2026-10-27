package com.ssafy.bidlive.common.security;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/** CONNECT 인증 예외를 민감정보가 없는 공통 STOMP ERROR frame으로 변환합니다. */
@Component
public class StompConnectErrorHandler extends StompSubProtocolErrorHandler {

	private static final String ERROR_TYPE = "ERROR";
	private static final byte[] FALLBACK_ERROR_BODY = """
		{"type":"ERROR","data":{"code":"AUTH_INVALID","message":"인증 정보가 유효하지 않습니다."}}
		""".strip().getBytes(StandardCharsets.UTF_8);

	private final ObjectMapper objectMapper;

	public StompConnectErrorHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public Message<byte[]> handleClientMessageProcessingError(
		Message<byte[]> clientMessage,
		Throwable exception
	) {
		StompAuthenticationException authenticationException = findAuthenticationException(exception);
		if (authenticationException == null) {
			return super.handleClientMessageProcessingError(clientMessage, exception);
		}
		return errorFrame(authenticationException.errorCode());
	}

	private Message<byte[]> errorFrame(StompAuthenticationErrorCode errorCode) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
		accessor.setContentType(MediaType.APPLICATION_JSON);
		accessor.setMessage(errorCode.name());
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(errorBody(errorCode), accessor.getMessageHeaders());
	}

	private byte[] errorBody(StompAuthenticationErrorCode errorCode) {
		try {
			return objectMapper.writeValueAsBytes(new ErrorEnvelope(
				ERROR_TYPE,
				new ErrorData(errorCode.code(), errorCode.publicMessage())
			));
		} catch (JsonProcessingException exception) {
			return FALLBACK_ERROR_BODY.clone();
		}
	}

	private StompAuthenticationException findAuthenticationException(Throwable exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof StompAuthenticationException authenticationException) {
				return authenticationException;
			}
			if (current == current.getCause()) {
				return null;
			}
			current = current.getCause();
		}
		return null;
	}

	private record ErrorEnvelope(String type, ErrorData data) {
	}

	private record ErrorData(String code, String message) {
	}
}
