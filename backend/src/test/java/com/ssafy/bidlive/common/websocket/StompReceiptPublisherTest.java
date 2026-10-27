package com.ssafy.bidlive.common.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/** STOMP RECEIPT frame의 세션 격리와 receipt-id header 구성을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class StompReceiptPublisherTest {

	private static final String SESSION_ID = "session-251";
	private static final String RECEIPT_ID = "renew-receipt-1";

	@Mock
	private MessageChannel clientOutboundChannel;

	private StompReceiptPublisher publisher;

	@BeforeEach
	void setUp() {
		publisher = new StompReceiptPublisher(clientOutboundChannel);
	}

	@Test
	void sendsReceiptToOriginalSession() {
		when(clientOutboundChannel.send(any())).thenReturn(true);

		publisher.publish(SESSION_ID, RECEIPT_ID);

		ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(
			Message.class
		);
		verify(clientOutboundChannel).send(messageCaptor.capture());
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
			messageCaptor.getValue(),
			StompHeaderAccessor.class
		);
		assertThat(accessor).isNotNull();
		assertThat(accessor.getCommand()).isEqualTo(StompCommand.RECEIPT);
		assertThat(accessor.getSessionId()).isEqualTo(SESSION_ID);
		assertThat(accessor.getReceiptId()).isEqualTo(RECEIPT_ID);
		assertThat(messageCaptor.getValue().getPayload()).isEqualTo(new byte[0]);
	}

	@Test
	void rejectsMissingSessionId() {
		assertThatThrownBy(() -> publisher.publish(null, RECEIPT_ID))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("sessionId");
	}

	@Test
	void rejectsMissingReceiptId() {
		assertThatThrownBy(() -> publisher.publish(SESSION_ID, " "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("receiptId은 비어 있을 수 없습니다.");
	}

	@Test
	void reportsOutboundDeliveryFailure() {
		when(clientOutboundChannel.send(any())).thenReturn(false);

		assertThatThrownBy(() -> publisher.publish(SESSION_ID, RECEIPT_ID))
			.isInstanceOf(MessageDeliveryException.class)
			.hasMessageContaining("STOMP RECEIPT frame 전송에 실패했습니다.");
	}
}
