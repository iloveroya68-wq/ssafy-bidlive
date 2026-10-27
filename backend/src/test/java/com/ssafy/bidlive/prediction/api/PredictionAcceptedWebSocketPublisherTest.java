package com.ssafy.bidlive.prediction.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ssafy.bidlive.common.event.WsEvent;
import com.ssafy.bidlive.prediction.api.dto.PredictionAcceptedData;
import com.ssafy.bidlive.prediction.application.PredictionAcceptedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** PREDICTION_ACCEPTED가 커밋 후 공개 집계만 브로드캐스트하는지 검증한다. */
class PredictionAcceptedWebSocketPublisherTest {

	private final SimpMessagingTemplate messagingTemplate = mock(
		SimpMessagingTemplate.class
	);
	private final PredictionAcceptedWebSocketPublisher publisher =
		new PredictionAcceptedWebSocketPublisher(messagingTemplate);

	@Test
	void publishesPredictionAcceptedPayloadToAuctionTopic() {
		publisher.onPredictionAccepted(new PredictionAcceptedEvent(100L, 9L));

		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/100"),
			payload.capture()
		);

		@SuppressWarnings("unchecked")
		WsEvent<PredictionAcceptedData> message =
			(WsEvent<PredictionAcceptedData>) payload.getValue();
		assertThat(message.type()).isEqualTo("PREDICTION_ACCEPTED");
		assertThat(message.data()).isEqualTo(new PredictionAcceptedData(
			9L,
			"누군가 예측에 참여했습니다!"
		));
	}

	@Test
	void publishesOnlyAfterTransactionCommit() throws NoSuchMethodException {
		TransactionalEventListener listener =
			PredictionAcceptedWebSocketPublisher.class
				.getMethod(
					"onPredictionAccepted",
					PredictionAcceptedEvent.class
				)
				.getAnnotation(TransactionalEventListener.class);

		assertThat(listener).isNotNull();
		assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}
}
