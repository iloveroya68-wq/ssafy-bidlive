package com.ssafy.bidlive.prediction.api;

import com.ssafy.bidlive.prediction.api.dto.PredictionSettledMessage;
import com.ssafy.bidlive.prediction.application.PredictionSettlementCompletedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 정산 트랜잭션 커밋 후 경매방 전체에 예측 정산 완료를 브로드캐스트한다. */
@Component
public class PredictionSettledWebSocketPublisher {

	private static final String DESTINATION_PREFIX = "/topic/auctions/";

	private final SimpMessagingTemplate messagingTemplate;

	public PredictionSettledWebSocketPublisher(
		SimpMessagingTemplate messagingTemplate
	) {
		this.messagingTemplate = messagingTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(PredictionSettlementCompletedEvent event) {
		messagingTemplate.convertAndSend(
			DESTINATION_PREFIX + event.auctionId(),
			PredictionSettledMessage.completed()
		);
	}
}
