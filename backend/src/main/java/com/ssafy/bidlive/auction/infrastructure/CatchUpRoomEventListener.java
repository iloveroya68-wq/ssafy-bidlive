package com.ssafy.bidlive.auction.infrastructure;

import com.ssafy.bidlive.auction.application.CatchUpSummaryStore;
import com.ssafy.bidlive.auction.domain.AuctionExtendedEvent;
import com.ssafy.bidlive.auction.domain.AuctionStartedEvent;
import com.ssafy.bidlive.bid.application.BidAcceptedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 경매 이벤트에서 비공개 값은 버리고 AI-03 공개 집계만 Redis에 남긴다. */
@Component
public class CatchUpRoomEventListener {

	private final CatchUpSummaryStore store;
	private final CatchUpSummaryProperties properties;

	public CatchUpRoomEventListener(
		CatchUpSummaryStore store,
		CatchUpSummaryProperties properties
	) {
		this.store = store;
		this.properties = properties;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onStarted(AuctionStartedEvent event) {
		store.recordStarted(
			event.auctionId(),
			event.startedAt(),
			event.endAt(),
			properties.getSourceTtl()
		);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onBidAccepted(BidAcceptedEvent event) {
		store.recordBidCounts(
			event.auctionId(),
			event.bidCount(),
			event.participantCount(),
			properties.getSourceTtl()
		);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onExtended(AuctionExtendedEvent event) {
		store.recordExtension(
			event.auctionId(),
			event.endAt(),
			event.extensionCount(),
			properties.getSourceTtl()
		);
	}
}
