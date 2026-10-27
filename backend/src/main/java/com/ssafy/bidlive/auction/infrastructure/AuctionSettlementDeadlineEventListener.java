package com.ssafy.bidlive.auction.infrastructure;

import java.util.Objects;

import com.ssafy.bidlive.auction.domain.AuctionExtendedEvent;
import com.ssafy.bidlive.auction.domain.AuctionStartedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 경매 시작·연장 사실을 최신 마감 시각의 비크리 정산 예약으로 연결한다. */
@Component
public class AuctionSettlementDeadlineEventListener {

	private final AuctionSettlementDeadlineScheduler deadlineScheduler;

	public AuctionSettlementDeadlineEventListener(
		AuctionSettlementDeadlineScheduler deadlineScheduler
	) {
		this.deadlineScheduler = Objects.requireNonNull(
			deadlineScheduler,
			"deadlineScheduler"
		);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAuctionStarted(AuctionStartedEvent event) {
		Objects.requireNonNull(event, "event");
		deadlineScheduler.schedule(event.auctionId(), event.endAt());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAuctionExtended(AuctionExtendedEvent event) {
		Objects.requireNonNull(event, "event");
		deadlineScheduler.schedule(event.auctionId(), event.endAt());
	}
}
