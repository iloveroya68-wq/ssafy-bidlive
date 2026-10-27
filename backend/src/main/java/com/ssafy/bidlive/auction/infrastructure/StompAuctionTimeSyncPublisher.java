package com.ssafy.bidlive.auction.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

import com.ssafy.bidlive.auction.application.AuctionTimeSyncPublisher;
import com.ssafy.bidlive.common.event.WsEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** 경매 마감 시각과 메시지 생성 시각을 TIME_SYNC 계약으로 변환해 방 전체에 발행한다. */
@Component
public class StompAuctionTimeSyncPublisher implements AuctionTimeSyncPublisher {

	static final String DESTINATION_PATTERN = "/topic/auctions/%d";
	static final String EVENT_TYPE = "TIME_SYNC";

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	private final SimpMessagingTemplate messagingTemplate;
	private final Clock clock;

	@Autowired
	public StompAuctionTimeSyncPublisher(
		SimpMessagingTemplate messagingTemplate
	) {
		this(messagingTemplate, Clock.systemUTC());
	}

	StompAuctionTimeSyncPublisher(
		SimpMessagingTemplate messagingTemplate,
		Clock clock
	) {
		this.messagingTemplate = Objects.requireNonNull(
			messagingTemplate,
			"messagingTemplate"
		);
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public void publish(long auctionId, Instant endAt) {
		Objects.requireNonNull(endAt, "endAt");
		messagingTemplate.convertAndSend(
			DESTINATION_PATTERN.formatted(auctionId),
			new WsEvent<>(
				EVENT_TYPE,
				new TimeSyncData(
					atKorea(clock.instant()),
					atKorea(endAt)
				)
			)
		);
	}

	private static OffsetDateTime atKorea(Instant instant) {
		return instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}

	record TimeSyncData(
		OffsetDateTime serverTime,
		OffsetDateTime endAt
	) {
	}
}
