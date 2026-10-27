package com.ssafy.bidlive.auction.application;

import static com.ssafy.bidlive.auction.application.AuctionSettlementClaimResult.ALREADY_ENDED;
import static com.ssafy.bidlive.auction.application.AuctionSettlementClaimResult.ALREADY_SETTLING;
import static com.ssafy.bidlive.auction.application.AuctionSettlementClaimResult.AUCTION_NOT_DUE;
import static com.ssafy.bidlive.auction.application.AuctionSettlementClaimResult.AUCTION_NOT_FOUND;
import static com.ssafy.bidlive.auction.application.AuctionSettlementClaimResult.AUCTION_NOT_LIVE;
import static com.ssafy.bidlive.auction.application.AuctionSettlementClaimResult.CLAIMED;
import static com.ssafy.bidlive.auction.application.AuctionSettlementCompletionResult.COMPLETED;
import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.ALREADY_COMPLETED;
import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.NO_SALE;
import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.SETTLED;
import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.SKIPPED_NOT_DUE;
import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.SKIPPED_NOT_LIVE;
import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.SKIPPED_REDIS_NOT_FOUND;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Redis 선점부터 DB 정산·Redis 완료·결과 알림까지 BID-07 전체 흐름을 조립한다. */
@Service
public class AuctionSettlementService {

	private final AuctionSettlementClaimProcessor claimProcessor;
	private final AuctionSettlementCompletionProcessor completionProcessor;
	private final AuctionSettlementTransactionService transactionService;
	private final AuctionSettlementNotificationPublisher notificationPublisher;
	private final Clock clock;

	@Autowired
	public AuctionSettlementService(
		AuctionSettlementClaimProcessor claimProcessor,
		AuctionSettlementCompletionProcessor completionProcessor,
		AuctionSettlementTransactionService transactionService,
		AuctionSettlementNotificationPublisher notificationPublisher
	) {
		this(
			claimProcessor,
			completionProcessor,
			transactionService,
			notificationPublisher,
			Clock.systemUTC()
		);
	}

	public AuctionSettlementService(
		AuctionSettlementClaimProcessor claimProcessor,
		AuctionSettlementCompletionProcessor completionProcessor,
		AuctionSettlementTransactionService transactionService,
		AuctionSettlementNotificationPublisher notificationPublisher,
		Clock clock
	) {
		this.claimProcessor = claimProcessor;
		this.completionProcessor = completionProcessor;
		this.transactionService = transactionService;
		this.notificationPublisher = notificationPublisher;
		this.clock = clock;
	}

	public AuctionSettlementResult settle(long auctionId) {
		if (auctionId <= 0) {
			throw new IllegalArgumentException(
				"auctionId must be positive"
			);
		}
		Instant requestedAt = clock.instant();
		AuctionSettlementClaimResult claim = claimProcessor.claim(
			new AuctionSettlementClaimCommand(auctionId, requestedAt)
		);
		AuctionSettlementResult skipped = skipped(auctionId, claim);
		if (skipped != null) {
			return skipped;
		}
		if (claim == CLAIMED) {
			notificationPublisher.publishEnded(auctionId, requestedAt);
		}

		AuctionSettlementResult result = transactionService.settle(
			auctionId,
			requestedAt
		);
		if (!result.completedNormally()
			&& result.status() != ALREADY_COMPLETED) {
			return result;
		}
		AuctionSettlementCompletionResult completion =
			completionProcessor.complete(
				new AuctionSettlementCompletionCommand(
					auctionId,
					clock.instant()
				)
			);
		if (!completion.completed()) {
			throw new IllegalStateException(
				"Redis settlement completion failed: " + completion
			);
		}
		if (result.completedNormally()
			&& (result.newlySettled() || completion == COMPLETED)) {
			publishResult(result);
		}
		return result;
	}

	private AuctionSettlementResult skipped(
		long auctionId,
		AuctionSettlementClaimResult claim
	) {
		if (claim == AUCTION_NOT_FOUND) {
			return AuctionSettlementResult.skipped(
				auctionId,
				SKIPPED_REDIS_NOT_FOUND
			);
		}
		if (claim == AUCTION_NOT_DUE) {
			return AuctionSettlementResult.skipped(
				auctionId,
				SKIPPED_NOT_DUE
			);
		}
		if (claim == AUCTION_NOT_LIVE) {
			return AuctionSettlementResult.skipped(
				auctionId,
				SKIPPED_NOT_LIVE
			);
		}
		if (claim == CLAIMED || claim == ALREADY_SETTLING
			|| claim == ALREADY_ENDED) {
			return null;
		}
		throw new IllegalStateException(
			"unsupported settlement claim result: " + claim
		);
	}

	private void publishResult(AuctionSettlementResult result) {
		if (result.status() == SETTLED) {
			notificationPublisher.publishSettled(result);
			return;
		}
		if (result.status() == NO_SALE) {
			notificationPublisher.publishNoSale(
				result.auctionId(),
				result.settledAt()
			);
		}
	}
}
