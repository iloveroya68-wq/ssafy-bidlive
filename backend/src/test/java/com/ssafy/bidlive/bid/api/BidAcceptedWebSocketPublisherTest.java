package com.ssafy.bidlive.bid.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ssafy.bidlive.bid.api.dto.BidAcceptedData;
import com.ssafy.bidlive.bid.application.BidAcceptedEvent;
import com.ssafy.bidlive.common.event.WsEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** BID_ACCEPTED가 커밋 후 공개 필드와 고정 Vibe 문구만 브로드캐스트하는지 검증한다. */
class BidAcceptedWebSocketPublisherTest {

	private final SimpMessagingTemplate messagingTemplate = mock(
		SimpMessagingTemplate.class
	);
	private final BidAcceptedWebSocketPublisher publisher =
		new BidAcceptedWebSocketPublisher(messagingTemplate);

	@Test
	void publishesBidAcceptedPayloadToAuctionTopic() {
		publisher.onBidAccepted(new BidAcceptedEvent(100L, 13L, 9L));

		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/100"),
			payload.capture()
		);

		@SuppressWarnings("unchecked")
		WsEvent<BidAcceptedData> message =
			(WsEvent<BidAcceptedData>) payload.getValue();
		assertThat(message.type()).isEqualTo("BID_ACCEPTED");
		assertThat(message.data()).isEqualTo(new BidAcceptedData(
			13L,
			9L,
			"누군가 베팅을 진행 중입니다!"
		));
	}

	@Test
	void publishesOnlyAfterTransactionCommit() throws NoSuchMethodException {
		TransactionalEventListener listener =
			BidAcceptedWebSocketPublisher.class
				.getMethod("onBidAccepted", BidAcceptedEvent.class)
				.getAnnotation(TransactionalEventListener.class);

		assertThat(listener).isNotNull();
		assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}
}
