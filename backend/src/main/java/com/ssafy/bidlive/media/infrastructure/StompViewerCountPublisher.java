package com.ssafy.bidlive.media.infrastructure;

import java.util.Objects;

import com.ssafy.bidlive.common.event.WsEvent;
import com.ssafy.bidlive.media.api.dto.ViewerCountData;
import com.ssafy.bidlive.media.application.ViewerCountUpdate;
import com.ssafy.bidlive.media.application.port.out.ViewerCountPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** 변경된 활성 STOMP 세션 수를 경매별 VIEWER_COUNT 이벤트로 발행합니다. */
@Component
public class StompViewerCountPublisher implements ViewerCountPublisher {

	static final String DESTINATION_PATTERN = "/topic/auctions/%d/viewers";
	static final String EVENT_TYPE = "VIEWER_COUNT";

	private final SimpMessagingTemplate messagingTemplate;

	public StompViewerCountPublisher(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = Objects.requireNonNull(
			messagingTemplate,
			"messagingTemplate"
		);
	}

	@Override
	public void publish(ViewerCountUpdate update) {
		Objects.requireNonNull(update, "update");

		messagingTemplate.convertAndSend(
			DESTINATION_PATTERN.formatted(update.auctionId()),
			new WsEvent<>(EVENT_TYPE, ViewerCountData.from(update))
		);
	}
}
