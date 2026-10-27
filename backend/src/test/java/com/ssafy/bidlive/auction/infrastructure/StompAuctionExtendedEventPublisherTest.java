package com.ssafy.bidlive.auction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.OffsetDateTime;

import com.ssafy.bidlive.auction.application.AuctionTimeSyncPublisher;
import com.ssafy.bidlive.auction.domain.AuctionExtendedEvent;
import com.ssafy.bidlive.common.event.WsEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** BID-03 성공 뒤 연장 결과와 시간 동기화 이벤트가 계약 순서로 발행되는지 검증한다. */
class StompAuctionExtendedEventPublisherTest {

	private static final Instant END_AT =
		Instant.parse("2026-07-20T12:25:00Z");

	@Test
	void sendsAuctionExtendedThenTimeSyncToRoomTopic() {
		SimpMessagingTemplate messagingTemplate = mock(
			SimpMessagingTemplate.class
		);
		AuctionTimeSyncPublisher timeSyncPublisher = mock(
			AuctionTimeSyncPublisher.class
		);
		StompAuctionExtendedEventPublisher publisher =
			new StompAuctionExtendedEventPublisher(
				messagingTemplate,
				timeSyncPublisher
			);

		publisher.onAuctionExtended(new AuctionExtendedEvent(
			100L,
			END_AT,
			2,
			1
		));

		ArgumentCaptor<Object> payload =
			ArgumentCaptor.forClass(Object.class);
		InOrder order = inOrder(messagingTemplate, timeSyncPublisher);
		order.verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/100"),
			payload.capture()
		);
		order.verify(timeSyncPublisher).publish(100L, END_AT);

		@SuppressWarnings("unchecked")
		WsEvent<StompAuctionExtendedEventPublisher.AuctionExtendedData>
			extendedEvent = (WsEvent<
				StompAuctionExtendedEventPublisher.AuctionExtendedData
			>) payload.getValue();
		assertThat(extendedEvent.type()).isEqualTo("AUCTION_EXTENDED");
		assertThat(extendedEvent.data().endAt()).isEqualTo(
			OffsetDateTime.parse("2026-07-20T21:25:00+09:00")
		);
		assertThat(extendedEvent.data().extensionCount()).isEqualTo(2);
		assertThat(extendedEvent.data().remainingExtensions()).isEqualTo(1);
	}

	@Test
	void keepsPayloadFieldsLimitedToEachEventContract() {
		assertThat(StompAuctionExtendedEventPublisher.AuctionExtendedData.class
			.getRecordComponents())
			.extracting(java.lang.reflect.RecordComponent::getName)
			.containsExactly(
				"endAt",
				"extensionCount",
				"remainingExtensions"
			);
	}
}
