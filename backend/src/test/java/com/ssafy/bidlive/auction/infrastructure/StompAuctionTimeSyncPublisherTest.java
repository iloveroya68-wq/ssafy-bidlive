package com.ssafy.bidlive.auction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.ssafy.bidlive.common.event.WsEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** 공통 TIME_SYNC 발행기가 서버 생성 시각과 마감 시각만 계약 형식으로 보내는지 검증한다. */
class StompAuctionTimeSyncPublisherTest {

	private static final Instant END_AT =
		Instant.parse("2026-07-20T12:25:00Z");
	private static final Instant SERVER_TIME =
		Instant.parse("2026-07-20T12:20:03Z");

	@Test
	void sendsTimeSyncToRoomTopic() {
		SimpMessagingTemplate messagingTemplate = mock(
			SimpMessagingTemplate.class
		);
		StompAuctionTimeSyncPublisher publisher =
			new StompAuctionTimeSyncPublisher(
				messagingTemplate,
				Clock.fixed(SERVER_TIME, ZoneOffset.UTC)
			);

		publisher.publish(100L, END_AT);

		ArgumentCaptor<Object> payload =
			ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/100"),
			payload.capture()
		);

		@SuppressWarnings("unchecked")
		WsEvent<StompAuctionTimeSyncPublisher.TimeSyncData> event =
			(WsEvent<StompAuctionTimeSyncPublisher.TimeSyncData>) payload.getValue();
		assertThat(event.type()).isEqualTo("TIME_SYNC");
		assertThat(event.data().serverTime()).isEqualTo(
			OffsetDateTime.parse("2026-07-20T21:20:03+09:00")
		);
		assertThat(event.data().endAt()).isEqualTo(
			OffsetDateTime.parse("2026-07-20T21:25:00+09:00")
		);
	}

	@Test
	void keepsPayloadFieldsLimitedToTimeContract() {
		assertThat(StompAuctionTimeSyncPublisher.TimeSyncData.class
			.getRecordComponents())
			.extracting(java.lang.reflect.RecordComponent::getName)
			.containsExactly("serverTime", "endAt");
	}
}
