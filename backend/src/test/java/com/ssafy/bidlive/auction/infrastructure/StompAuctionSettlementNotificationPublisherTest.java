package com.ssafy.bidlive.auction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import com.ssafy.bidlive.auction.application.AuctionSettlementResult;
import com.ssafy.bidlive.auction.application.AuctionSettlementStatus;
import com.ssafy.bidlive.auction.domain.VickreyPriceDecisionType;
import com.ssafy.bidlive.common.event.WsEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** BID-07 STOMP 이벤트의 타입·목적지·확정 payload를 검증한다. */
@ExtendWith(MockitoExtension.class)
class StompAuctionSettlementNotificationPublisherTest {

	private static final Instant ENDED_AT =
		Instant.parse("2026-07-20T12:20:00Z");

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	private StompAuctionSettlementNotificationPublisher publisher;

	@BeforeEach
	void setUp() {
		publisher = new StompAuctionSettlementNotificationPublisher(
			messagingTemplate
		);
	}

	@Test
	void publishesSettlingStartPayload() {
		publisher.publishEnded(42L, ENDED_AT);

		WsEvent<?> event = capturedEvent();
		assertThat(event.type()).isEqualTo("AUCTION_ENDED");
		assertThat(event.data()).isEqualTo(
			new StompAuctionSettlementNotificationPublisher.AuctionEndedData(
				ENDED_AT,
				"SETTLING"
			)
		);
	}

	@Test
	void publishesSoldResultWithoutAuctionIdInPayload() {
		publisher.publishSettled(new AuctionSettlementResult(
			42L,
			AuctionSettlementStatus.SETTLED,
			true,
			100L,
			"낙찰*",
			82_000L,
			74_000L,
			50_000L,
			74_000L,
			VickreyPriceDecisionType.SECOND_PRICE,
			ENDED_AT
		));

		WsEvent<?> event = capturedEvent();
		assertThat(event.type()).isEqualTo("AUCTION_SETTLED");
		assertThat(event.data()).isEqualTo(
			new StompAuctionSettlementNotificationPublisher.AuctionSettledData(
				"낙찰*",
				82_000L,
				74_000L,
				74_000L,
				"SOLD"
			)
		);
	}

	@Test
	void publishesNoBidReasonAndEndedAt() {
		publisher.publishNoSale(42L, ENDED_AT);

		WsEvent<?> event = capturedEvent();
		assertThat(event.type()).isEqualTo("AUCTION_NO_SALE");
		assertThat(event.data()).isEqualTo(
			new StompAuctionSettlementNotificationPublisher.AuctionNoSaleData(
				"NO_BID",
				ENDED_AT
			)
		);
	}

	private WsEvent<?> capturedEvent() {
		ArgumentCaptor<WsEvent<?>> captor = ArgumentCaptor.forClass(
			WsEvent.class
		);
		verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/42"),
			captor.capture()
		);
		return captor.getValue();
	}
}
