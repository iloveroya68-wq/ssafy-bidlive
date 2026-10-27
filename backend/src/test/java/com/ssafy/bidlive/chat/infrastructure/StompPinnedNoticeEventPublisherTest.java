package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ssafy.bidlive.chat.domain.NoticePinnedEvent;
import com.ssafy.bidlive.chat.domain.NoticeUnpinnedEvent;
import com.ssafy.bidlive.chat.domain.PinnedNotice;
import com.ssafy.bidlive.common.event.WsEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** 고정 공지 변경이 채팅 topic의 공통 WebSocket envelope로 발행되는지 검증합니다. */
class StompPinnedNoticeEventPublisherTest {

	private final SimpMessagingTemplate messagingTemplate =
		mock(SimpMessagingTemplate.class);
	private final StompPinnedNoticeEventPublisher publisher =
		new StompPinnedNoticeEventPublisher(messagingTemplate);

	@Test
	void publishesPinnedNotice() {
		publisher.onPinned(new NoticePinnedEvent(
			42L,
			new PinnedNotice("101", "배송비 포함입니다.")
		));

		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/42/chat"),
			payload.capture()
		);
		@SuppressWarnings("unchecked")
		WsEvent<StompPinnedNoticeEventPublisher.NoticePinnedData> event =
			(WsEvent<StompPinnedNoticeEventPublisher.NoticePinnedData>) payload.getValue();
		assertThat(event.type()).isEqualTo("NOTICE_PINNED");
		assertThat(event.data().messageId()).isEqualTo("101");
		assertThat(event.data().content()).isEqualTo("배송비 포함입니다.");
	}

	@Test
	void publishesUnpinnedNoticeWithNonNullData() {
		publisher.onUnpinned(new NoticeUnpinnedEvent(42L));

		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/42/chat"),
			payload.capture()
		);
		@SuppressWarnings("unchecked")
		WsEvent<StompPinnedNoticeEventPublisher.NoticeUnpinnedData> event =
			(WsEvent<StompPinnedNoticeEventPublisher.NoticeUnpinnedData>) payload.getValue();
		assertThat(event.type()).isEqualTo("NOTICE_UNPINNED");
		assertThat(event.data()).isNotNull();
	}
}
