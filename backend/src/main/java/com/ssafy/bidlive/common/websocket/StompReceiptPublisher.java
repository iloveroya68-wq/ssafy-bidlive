package com.ssafy.bidlive.common.websocket;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/** 처리에 성공한 STOMP 요청의 RECEIPT frame을 원래 WebSocket 세션으로 전송합니다. */
@Component
public class StompReceiptPublisher {

	private final MessageChannel clientOutboundChannel;

	public StompReceiptPublisher(
		@Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel
	) {
		this.clientOutboundChannel = clientOutboundChannel;
	}

	public void publish(String sessionId, String receiptId) {
		String validatedSessionId = requireText(sessionId, "sessionId");
		String validatedReceiptId = requireText(receiptId, "receiptId");
		StompHeaderAccessor accessor = StompHeaderAccessor.create(
			StompCommand.RECEIPT
		);
		accessor.setSessionId(validatedSessionId);
		accessor.setReceiptId(validatedReceiptId);
		accessor.setLeaveMutable(true);
		Message<byte[]> receipt = MessageBuilder.createMessage(
			new byte[0],
			accessor.getMessageHeaders()
		);

		if (!clientOutboundChannel.send(receipt)) {
			throw new MessageDeliveryException(
				receipt,
				"STOMP RECEIPT frame 전송에 실패했습니다."
			);
		}
	}

	private String requireText(String value, String fieldName) {
		Objects.requireNonNull(value, fieldName);
		if (value.isBlank()) {
			throw new IllegalArgumentException(
				fieldName + "은 비어 있을 수 없습니다."
			);
		}
		return value;
	}
}
