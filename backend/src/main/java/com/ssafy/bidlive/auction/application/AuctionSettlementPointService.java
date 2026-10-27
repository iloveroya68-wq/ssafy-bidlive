package com.ssafy.bidlive.auction.application;

import java.util.Comparator;
import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.OutstandingBidHold;
import com.ssafy.bidlive.auction.domain.VickreySettlementDecision;
import com.ssafy.bidlive.auction.domain.VickreySettlementOutcome;
import com.ssafy.bidlive.wallet.application.TradePointOperationService;
import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비크리 결정에 따라 승자 포인트를 차감하고 나머지 입찰 홀드를 해제한다. */
@Service
public class AuctionSettlementPointService {

	private static final String IDEMPOTENCY_PREFIX = "bid-07:";
	private static final Comparator<OutstandingBidHold> HOLD_ORDER =
		Comparator.comparingLong(OutstandingBidHold::memberId)
			.thenComparingLong(OutstandingBidHold::bidId);

	private final AuctionSettlementPersistence persistence;
	private final TradePointOperationService tradePointOperationService;

	public AuctionSettlementPointService(
		AuctionSettlementPersistence persistence,
		TradePointOperationService tradePointOperationService
	) {
		this.persistence = persistence;
		this.tradePointOperationService = tradePointOperationService;
	}

	@Transactional
	public AuctionSettlementPointResult settle(
		long auctionId,
		VickreySettlementDecision decision
	) {
		validateInput(auctionId, decision);
		List<OutstandingBidHold> holds = persistence
			.findOutstandingBidHolds(auctionId)
			.stream()
			.sorted(HOLD_ORDER)
			.toList();

		if (decision.outcome() == VickreySettlementOutcome.NO_SALE) {
			if (!holds.isEmpty()) {
				throw new IllegalStateException(
					"no-sale auction must not have outstanding bid holds"
				);
			}
			return new AuctionSettlementPointResult(
				auctionId, 0L, 0L, 0, 0
			);
		}

		long winnerId = decision.winnerId();
		long finalPrice = decision.finalPrice();
		validateWinnerHold(holds, winnerId, decision.highestBidAmount());

		long remainingSettlement = finalPrice;
		long settledAmount = 0L;
		long releasedAmount = 0L;
		int settlementCount = 0;
		int releaseCount = 0;

		for (OutstandingBidHold hold : holds) {
			if (hold.memberId() != winnerId) {
				release(auctionId, hold, hold.remainingHold());
				releasedAmount = Math.addExact(
					releasedAmount,
					hold.remainingHold()
				);
				releaseCount++;
				continue;
			}

			long amountToSettle = Math.min(
				hold.remainingHold(),
				remainingSettlement
			);
			if (amountToSettle > 0) {
				settle(auctionId, hold, amountToSettle);
				remainingSettlement = Math.subtractExact(
					remainingSettlement,
					amountToSettle
				);
				settledAmount = Math.addExact(
					settledAmount,
					amountToSettle
				);
				settlementCount++;
			}

			long amountToRelease = Math.subtractExact(
				hold.remainingHold(),
				amountToSettle
			);
			if (amountToRelease > 0) {
				release(auctionId, hold, amountToRelease);
				releasedAmount = Math.addExact(
					releasedAmount,
					amountToRelease
				);
				releaseCount++;
			}
		}

		if (remainingSettlement != 0) {
			throw new IllegalStateException(
				"winner hold is insufficient for final price"
			);
		}
		return new AuctionSettlementPointResult(
			auctionId,
			settledAmount,
			releasedAmount,
			settlementCount,
			releaseCount
		);
	}

	private void validateWinnerHold(
		List<OutstandingBidHold> holds,
		long winnerId,
		long highestBidAmount
	) {
		long winnerHold = holds.stream()
			.filter(hold -> hold.memberId() == winnerId)
			.mapToLong(OutstandingBidHold::remainingHold)
			.reduce(0L, Math::addExact);
		if (winnerHold != highestBidAmount) {
			throw new IllegalStateException(
				"winner hold must equal the highest bid amount"
			);
		}
	}

	private void settle(
		long auctionId,
		OutstandingBidHold hold,
		long amount
	) {
		tradePointOperationService.settle(
			PointLedgerReferenceType.BID,
			hold.bidId(),
			hold.memberId(),
			amount,
			idempotencyKey(auctionId, hold.bidId(), "settle")
		);
	}

	private void release(
		long auctionId,
		OutstandingBidHold hold,
		long amount
	) {
		tradePointOperationService.release(
			PointLedgerReferenceType.BID,
			hold.bidId(),
			hold.memberId(),
			amount,
			idempotencyKey(auctionId, hold.bidId(), "release")
		);
	}

	private String idempotencyKey(
		long auctionId,
		long bidId,
		String operation
	) {
		return IDEMPOTENCY_PREFIX + auctionId + ":bid:" + bidId + ":"
			+ operation;
	}

	private void validateInput(
		long auctionId,
		VickreySettlementDecision decision
	) {
		if (auctionId <= 0) {
			throw new IllegalArgumentException(
				"auctionId must be positive"
			);
		}
		if (decision == null) {
			throw new IllegalArgumentException(
				"decision must not be null"
			);
		}
	}
}
