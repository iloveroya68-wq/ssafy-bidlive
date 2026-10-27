package com.ssafy.bidlive.auction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.OffsetDateTime;

import com.ssafy.bidlive.auction.application.AuctionTimeSyncPublisher;
import com.ssafy.bidlive.auction.domain.AuctionStartedEvent;
import com.ssafy.bidlive.common.event.WsEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** `AUCTION_STARTED`의 destination·type·data가 이벤트 명세와 같은지 검증한다. */
class StompAuctionStartedEventPublisherTest {

	@Test
	void sendsAuctionStartedThenTimeSyncToRoomTopic() {
		SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
		AuctionTimeSyncPublisher timeSyncPublisher = mock(
			AuctionTimeSyncPublisher.class
		);
		Instant endAt = Instant.parse("2026-07-21T11:17:13Z");
		StompAuctionStartedEventPublisher publisher =
			new StompAuctionStartedEventPublisher(
				messagingTemplate,
				timeSyncPublisher
			);

		publisher.onAuctionStarted(new AuctionStartedEvent(
			100L,
			25L,
			50_000L,
			Instant.parse("2026-07-21T11:02:13Z"),
			endAt
		));

		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		InOrder order = inOrder(messagingTemplate, timeSyncPublisher);
		order.verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/100"),
			payload.capture()
		);
		order.verify(timeSyncPublisher).publish(100L, endAt);

		@SuppressWarnings("unchecked")
		WsEvent<StompAuctionStartedEventPublisher.AuctionStartedData> event =
			(WsEvent<StompAuctionStartedEventPublisher.AuctionStartedData>) payload.getValue();
		assertThat(event.type()).isEqualTo("AUCTION_STARTED");
		assertThat(event.data().startedAt())
			.isEqualTo(OffsetDateTime.parse("2026-07-21T20:02:13+09:00"));
		assertThat(event.data().endAt())
			.isEqualTo(OffsetDateTime.parse("2026-07-21T20:17:13+09:00"));
	}

	/**
	 * `data`에 `auctionId`를 넣지 않는 것이 공통 메시지 형식 규칙이다(destination에 이미 있다).
	 * 레코드 컴포넌트가 둘뿐인 것으로 그 규칙을 잠근다 — 늘어나면 이 테스트가 깨진다.
	 */
	@Test
	void keepsDataFieldsLimitedToTheEventContract() {
		assertThat(StompAuctionStartedEventPublisher.AuctionStartedData.class
			.getRecordComponents())
			.extracting(java.lang.reflect.RecordComponent::getName)
			.containsExactly("startedAt", "endAt");
	}
}
