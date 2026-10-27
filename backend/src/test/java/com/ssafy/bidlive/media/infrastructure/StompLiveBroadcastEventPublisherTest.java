package com.ssafy.bidlive.media.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.common.event.WsEvent;
import com.ssafy.bidlive.media.domain.LiveBroadcastEndedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 종료 이벤트가 방별 destination으로 공통 type·data 형식으로 나가고 커밋 이후에만 수신되는지 검증합니다. */
class StompLiveBroadcastEventPublisherTest {

	private static final OffsetDateTime ENDED_AT =
		OffsetDateTime.parse("2026-07-20T21:40:00+09:00");

	private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
	private final StompLiveBroadcastEventPublisher publisher =
		new StompLiveBroadcastEventPublisher(messagingTemplate);

	@Test
	void sendsLiveEndedEventToRoomDestination() {
		publisher.onLiveBroadcastEnded(new LiveBroadcastEndedEvent("room-123", ENDED_AT));

		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate).convertAndSend(eq("/topic/rooms/room-123/live"), payload.capture());

		assertThat(payload.getValue()).isEqualTo(
			new WsEvent<>(
				"LIVE_ENDED",
				new StompLiveBroadcastEventPublisher.LiveEndedData(ENDED_AT)
			)
		);
	}

	@Test
	void receivesEndedEventOnlyAfterCommit() throws NoSuchMethodException {
		TransactionalEventListener listener = StompLiveBroadcastEventPublisher.class
			.getMethod("onLiveBroadcastEnded", LiveBroadcastEndedEvent.class)
			.getAnnotation(TransactionalEventListener.class);

		assertThat(listener).isNotNull();
		assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}
}
