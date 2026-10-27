package com.ssafy.bidlive.chat.infrastructure;

import com.ssafy.bidlive.chat.domain.NoticePinnedEvent;
import com.ssafy.bidlive.chat.domain.NoticeUnpinnedEvent;
import com.ssafy.bidlive.chat.domain.PinnedNotice;
import com.ssafy.bidlive.common.event.WsEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 고정 공지 변경만 경매 채팅 구독자에게 NOTICE 이벤트로 전달합니다. */
@Component
public class StompPinnedNoticeEventPublisher {

	static final String DESTINATION_PATTERN = "/topic/auctions/%d/chat";
	static final String PINNED_EVENT_TYPE = "NOTICE_PINNED";
	static final String UNPINNED_EVENT_TYPE = "NOTICE_UNPINNED";

	private final SimpMessagingTemplate messagingTemplate;

	public StompPinnedNoticeEventPublisher(
		SimpMessagingTemplate messagingTemplate
	) {
		this.messagingTemplate = messagingTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPinned(NoticePinnedEvent event) {
		PinnedNotice notice = event.notice();
		messagingTemplate.convertAndSend(
			DESTINATION_PATTERN.formatted(event.auctionId()),
			new WsEvent<>(
				PINNED_EVENT_TYPE,
				new NoticePinnedData(notice.messageId(), notice.content())
			)
		);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onUnpinned(NoticeUnpinnedEvent event) {
		messagingTemplate.convertAndSend(
			DESTINATION_PATTERN.formatted(event.auctionId()),
			new WsEvent<>(UNPINNED_EVENT_TYPE, new NoticeUnpinnedData())
		);
	}

	/** NOTICE_PINNED data가 현재 고정 공지 식별자와 내용을 전달합니다. */
	record NoticePinnedData(String messageId, String content) {
	}

	/** NOTICE_UNPINNED는 추가 상태 없이 고정 해제 사실만 전달합니다. */
	record NoticeUnpinnedData() {
	}
}
