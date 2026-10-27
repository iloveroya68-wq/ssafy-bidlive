package com.ssafy.bidlive.prediction.api;

import java.util.Objects;

import com.ssafy.bidlive.common.event.WsEvent;
import com.ssafy.bidlive.prediction.api.dto.PredictionAcceptedData;
import com.ssafy.bidlive.prediction.application.PredictionAcceptedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 신규 예측의 공개 집계를 경매방 구독자에게 전달한다. */
@Component
public class PredictionAcceptedWebSocketPublisher {

	static final String DESTINATION_PATTERN = "/topic/auctions/%d";
	static final String EVENT_TYPE = "PREDICTION_ACCEPTED";
	static final String VIBE_MESSAGE = "누군가 예측에 참여했습니다!";

	private final SimpMessagingTemplate messagingTemplate;

	public PredictionAcceptedWebSocketPublisher(
		SimpMessagingTemplate messagingTemplate
	) {
		this.messagingTemplate = Objects.requireNonNull(
			messagingTemplate,
			"messagingTemplate"
		);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPredictionAccepted(PredictionAcceptedEvent event) {
		messagingTemplate.convertAndSend(
			DESTINATION_PATTERN.formatted(event.auctionId()),
			new WsEvent<>(
				EVENT_TYPE,
				new PredictionAcceptedData(
					event.participantCount(),
					VIBE_MESSAGE
				)
			)
		);
	}
}
