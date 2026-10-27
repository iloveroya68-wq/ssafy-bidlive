package com.ssafy.bidlive.auction.infrastructure;

import java.time.Instant;

import com.ssafy.bidlive.auction.application.AuctionSettlementNotificationPublisher;
import com.ssafy.bidlive.auction.application.AuctionSettlementResult;
import com.ssafy.bidlive.common.event.WsEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** BID-07의 집계 시작·낙찰·유찰 메시지를 경매방 STOMP 토픽으로 보낸다. */
@Component
public class StompAuctionSettlementNotificationPublisher
	implements AuctionSettlementNotificationPublisher {

	private static final String DESTINATION_PATTERN = "/topic/auctions/%d";

	private final SimpMessagingTemplate messagingTemplate;

	public StompAuctionSettlementNotificationPublisher(
		SimpMessagingTemplate messagingTemplate
	) {
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	public void publishEnded(long auctionId, Instant endedAt) {
		publish(
			auctionId,
			new WsEvent<>(
				"AUCTION_ENDED",
				new AuctionEndedData(endedAt, "SETTLING")
			)
		);
	}

	@Override
	public void publishSettled(AuctionSettlementResult result) {
		publish(
			result.auctionId(),
			new WsEvent<>(
				"AUCTION_SETTLED",
				new AuctionSettledData(
					result.winner(),
					result.highestBidAmount(),
					result.secondHighestBidAmount(),
					result.finalPrice(),
					"SOLD"
				)
			)
		);
	}

	@Override
	public void publishNoSale(long auctionId, Instant endedAt) {
		publish(
			auctionId,
			new WsEvent<>(
				"AUCTION_NO_SALE",
				new AuctionNoSaleData("NO_BID", endedAt)
			)
		);
	}

	private void publish(long auctionId, WsEvent<?> event) {
		messagingTemplate.convertAndSend(
			DESTINATION_PATTERN.formatted(auctionId),
			event
		);
	}

	record AuctionEndedData(Instant endedAt, String status) {
	}

	record AuctionSettledData(
		String winner,
		Long highestBidAmount,
		Long secondHighestBidAmount,
		Long finalPrice,
		String resultType
	) {
	}

	record AuctionNoSaleData(String reason, Instant endedAt) {
	}
}
