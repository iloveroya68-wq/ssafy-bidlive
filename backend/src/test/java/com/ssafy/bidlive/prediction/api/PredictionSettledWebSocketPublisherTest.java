package com.ssafy.bidlive.prediction.api;

import static org.mockito.Mockito.verify;

import com.ssafy.bidlive.prediction.api.dto.PredictionSettledMessage;
import com.ssafy.bidlive.prediction.application.PredictionSettlementCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** 정산 완료 메시지가 해당 경매의 공용 topic으로 발행되는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class PredictionSettledWebSocketPublisherTest {

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	@Test
	void broadcastsCompletionToAuctionTopic() {
		PredictionSettledWebSocketPublisher publisher =
			new PredictionSettledWebSocketPublisher(messagingTemplate);

		publisher.publish(new PredictionSettlementCompletedEvent(100L));

		verify(messagingTemplate).convertAndSend(
			"/topic/auctions/100",
			PredictionSettledMessage.completed()
		);
	}
}
