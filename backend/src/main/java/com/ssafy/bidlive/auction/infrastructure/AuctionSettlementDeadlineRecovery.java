package com.ssafy.bidlive.auction.infrastructure;

import java.util.Objects;

import com.ssafy.bidlive.auction.application.AuctionSettlementDeadlineReader;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 서버 기동 시 메모리에서 사라진 LIVE 경매의 정산 예약을 DB 기준으로 복구한다. */
@Component
public class AuctionSettlementDeadlineRecovery {

	private final AuctionSettlementDeadlineReader deadlineReader;
	private final AuctionSettlementDeadlineScheduler deadlineScheduler;

	public AuctionSettlementDeadlineRecovery(
		AuctionSettlementDeadlineReader deadlineReader,
		AuctionSettlementDeadlineScheduler deadlineScheduler
	) {
		this.deadlineReader = Objects.requireNonNull(
			deadlineReader,
			"deadlineReader"
		);
		this.deadlineScheduler = Objects.requireNonNull(
			deadlineScheduler,
			"deadlineScheduler"
		);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void recoverLiveAuctionDeadlines() {
		deadlineReader.findLiveDeadlines().forEach(deadline ->
			deadlineScheduler.schedule(
				deadline.auctionId(),
				deadline.endAt()
			)
		);
	}
}
