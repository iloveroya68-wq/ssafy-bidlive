package com.ssafy.bidlive.auction.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

import com.ssafy.bidlive.auction.application.AuctionTimeSyncPublisher;
import com.ssafy.bidlive.auction.domain.AuctionExtendedEvent;
import com.ssafy.bidlive.common.event.WsEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 BID-03 연장 결과를 발행한 뒤 공통 TIME_SYNC 발행기로 마감 시각을 동기화한다. */
@Component
public class StompAuctionExtendedEventPublisher {

	static final String DESTINATION_PATTERN = "/topic/auctions/%d";
	static final String AUCTION_EXTENDED_EVENT_TYPE = "AUCTION_EXTENDED";

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	private final SimpMessagingTemplate messagingTemplate;
	private final AuctionTimeSyncPublisher timeSyncPublisher;

	public StompAuctionExtendedEventPublisher(
		SimpMessagingTemplate messagingTemplate,
		AuctionTimeSyncPublisher timeSyncPublisher
	) {
		this.messagingTemplate = Objects.requireNonNull(
			messagingTemplate,
			"messagingTemplate"
		);
		this.timeSyncPublisher = Objects.requireNonNull(
			timeSyncPublisher,
			"timeSyncPublisher"
		);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAuctionExtended(AuctionExtendedEvent event) {
		String destination = DESTINATION_PATTERN.formatted(
			event.auctionId()
		);
		OffsetDateTime endAt = atKorea(event.endAt());
		messagingTemplate.convertAndSend(
			destination,
			new WsEvent<>(
				AUCTION_EXTENDED_EVENT_TYPE,
				new AuctionExtendedData(
					endAt,
					event.extensionCount(),
					event.remainingExtensions()
				)
			)
		);
		timeSyncPublisher.publish(event.auctionId(), event.endAt());
	}

	private static OffsetDateTime atKorea(Instant instant) {
		return instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}

	record AuctionExtendedData(
		OffsetDateTime endAt,
		int extensionCount,
		int remainingExtensions
	) {
	}
}
