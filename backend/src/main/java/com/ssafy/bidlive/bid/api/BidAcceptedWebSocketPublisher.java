package com.ssafy.bidlive.bid.api;

import java.util.Objects;

import com.ssafy.bidlive.bid.api.dto.BidAcceptedData;
import com.ssafy.bidlive.bid.application.BidAcceptedEvent;
import com.ssafy.bidlive.common.event.WsEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 승인 입찰을 공개 집계와 Vibe 문구로 조립해 경매방 구독자에게 전달한다. */
@Component
public class BidAcceptedWebSocketPublisher {

	static final String DESTINATION_PATTERN = "/topic/auctions/%d";
	static final String EVENT_TYPE = "BID_ACCEPTED";
	static final String VIBE_MESSAGE = "누군가 베팅을 진행 중입니다!";

	private final SimpMessagingTemplate messagingTemplate;

	public BidAcceptedWebSocketPublisher(
		SimpMessagingTemplate messagingTemplate
	) {
		this.messagingTemplate = Objects.requireNonNull(
			messagingTemplate,
			"messagingTemplate"
		);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onBidAccepted(BidAcceptedEvent event) {
		messagingTemplate.convertAndSend(
			DESTINATION_PATTERN.formatted(event.auctionId()),
			new WsEvent<>(
				EVENT_TYPE,
				new BidAcceptedData(
					event.bidCount(),
					event.participantCount(),
					VIBE_MESSAGE
				)
			)
		);
	}
}
