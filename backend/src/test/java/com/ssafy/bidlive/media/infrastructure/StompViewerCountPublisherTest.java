package com.ssafy.bidlive.media.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.common.event.WsEvent;
import com.ssafy.bidlive.media.api.dto.ViewerCountData;
import com.ssafy.bidlive.media.application.ViewerCountUpdate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** 활성 세션 수가 경매별 destination에 VIEWER_COUNT 형식으로 발행되는지 검증합니다. */
class StompViewerCountPublisherTest {

	private final SimpMessagingTemplate messagingTemplate = mock(
		SimpMessagingTemplate.class
	);
	private final StompViewerCountPublisher publisher =
		new StompViewerCountPublisher(messagingTemplate);

	@Test
	void publishesViewerCountEnvelopeToAuctionDestination() {
		OffsetDateTime measuredAt = OffsetDateTime.parse(
			"2026-07-20T21:03:12+09:00"
		);

		publisher.publish(new ViewerCountUpdate(42L, 168L, measuredAt));

		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/42/viewers"),
			payload.capture()
		);
		assertThat(payload.getValue()).isEqualTo(
			new WsEvent<>(
				"VIEWER_COUNT",
				new ViewerCountData(168L, "STOMP_SESSION", measuredAt)
			)
		);
	}
}
