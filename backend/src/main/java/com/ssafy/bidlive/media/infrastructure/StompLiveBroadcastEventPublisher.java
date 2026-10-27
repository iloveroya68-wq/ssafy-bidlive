package com.ssafy.bidlive.media.infrastructure;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.ssafy.bidlive.common.event.WsEvent;
import com.ssafy.bidlive.media.domain.LiveBroadcastEndedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 방송 종료 확정 사실을 방 구독자에게 STOMP로 내보내는 어댑터입니다.
 *
 * <p>커밋 이후에만 수신하므로 롤백된 종료는 시청자에게 나가지 않는다. `roomId`는 구독
 * destination에 이미 있어 공통 메시지 형식 규칙대로 `data`에서 제외한다.
 *
 * <p>방송은 경매가 아닌 방(room) 리소스이므로 destination도 경매 이벤트의
 * `/topic/auctions/{auctionId}`와 분리한다.
 */
@Component
public class StompLiveBroadcastEventPublisher {

	static final String LIVE_DESTINATION_PATTERN = "/topic/rooms/%s/live";
	static final String LIVE_ENDED_EVENT_TYPE = "LIVE_ENDED";

	private final SimpMessagingTemplate messagingTemplate;

	public StompLiveBroadcastEventPublisher(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = Objects.requireNonNull(messagingTemplate, "messagingTemplate");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onLiveBroadcastEnded(LiveBroadcastEndedEvent event) {
		messagingTemplate.convertAndSend(
			LIVE_DESTINATION_PATTERN.formatted(event.roomId()),
			new WsEvent<>(LIVE_ENDED_EVENT_TYPE, new LiveEndedData(event.endedAt()))
		);
	}

	/** 이벤트 종류별 `data`는 사건이 발생한 도메인이 소유하므로 envelope과 달리 공통 패키지로 올리지 않는다. */
	record LiveEndedData(OffsetDateTime endedAt) {
	}
}
