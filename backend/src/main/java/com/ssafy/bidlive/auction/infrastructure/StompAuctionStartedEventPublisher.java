package com.ssafy.bidlive.auction.infrastructure;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

import com.ssafy.bidlive.auction.application.AuctionTimeSyncPublisher;
import com.ssafy.bidlive.auction.domain.AuctionStartedEvent;
import com.ssafy.bidlive.common.event.WsEvent;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 방송 시작 사실을 경매방 구독자에게 STOMP로 내보내는 어댑터다 (ROOM-05, `AUCTION_STARTED`).
 *
 * <p>커밋 이후에만 수신하므로 롤백된 시작은 대기방 사용자에게 나가지 않는다.
 * `auctionId`는 구독 destination에 이미 있어 공통 메시지 형식 규칙대로 `data`에서 제외한다
 * (`WsEvent` 주석). 그래서 `data`는 이벤트 명세가 정한 `startedAt`·`endAt` 둘뿐이다.
 */
@Component
public class StompAuctionStartedEventPublisher {

	/**
	 * 입찰 실시간 상태 초기화(`RedisAuctionStateInitializer`, order 0) **뒤에** 돈다.
	 *
	 * <p>먼저 돌면 전원이 라이브로 넘어간 뒤 상태 Hash가 아직 없어 그 사이 입찰이
	 * `AUCTION_NOT_FOUND`로 거절된다 — 사용자가 이유를 알 수 없는 상태다. 순서를 명시하지 않으면
	 * 빈 등록 순서에 달려 결정적이지 않다 (MR !204 리뷰).
	 *
	 * <p><b>⚠️ 애노테이션은 리스너 메서드에 붙는다.</b> 클래스에 붙이면
	 * `ApplicationListenerMethodAdapter.resolveOrder(Method)`가 못 읽어 조용히 무시된다
	 * (S15P11A704-537).
	 */
	static final int LISTENER_ORDER = 100;

	static final String AUCTION_DESTINATION_PATTERN = "/topic/auctions/%d";
	static final String AUCTION_STARTED_EVENT_TYPE = "AUCTION_STARTED";

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	private final SimpMessagingTemplate messagingTemplate;
	private final AuctionTimeSyncPublisher timeSyncPublisher;

	public StompAuctionStartedEventPublisher(
		SimpMessagingTemplate messagingTemplate,
		AuctionTimeSyncPublisher timeSyncPublisher
	) {
		this.messagingTemplate = Objects.requireNonNull(messagingTemplate, "messagingTemplate");
		this.timeSyncPublisher = Objects.requireNonNull(timeSyncPublisher, "timeSyncPublisher");
	}

	@Order(LISTENER_ORDER)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAuctionStarted(AuctionStartedEvent event) {
		messagingTemplate.convertAndSend(
			AUCTION_DESTINATION_PATTERN.formatted(event.auctionId()),
			new WsEvent<>(
				AUCTION_STARTED_EVENT_TYPE,
				new AuctionStartedData(
					atKorea(event.startedAt()),
					atKorea(event.endAt())
				)
			)
		);
		timeSyncPublisher.publish(event.auctionId(), event.endAt());
	}

	private static OffsetDateTime atKorea(java.time.Instant instant) {
		return instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}

	/** 이벤트 종류별 `data`는 사건이 발생한 도메인이 소유하므로 공통 패키지로 올리지 않는다. */
	record AuctionStartedData(
		OffsetDateTime startedAt,
		OffsetDateTime endAt
	) {
	}
}
