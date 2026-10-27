package com.ssafy.bidlive.auction.application;

import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.ALREADY_COMPLETED;
import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.NO_SALE;
import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.SETTLED;
import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.SKIPPED_NOT_DUE;
import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.SKIPPED_NOT_FOUND;
import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.SKIPPED_NOT_LIVE;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.BidCounts;
import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.FinalBid;
import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.ResultWrite;
import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.SettlementTarget;
import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.StoredResult;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.auction.domain.SellerNicknameMask;
import com.ssafy.bidlive.auction.domain.VickreyPriceDecisionType;
import com.ssafy.bidlive.auction.domain.VickreySettlementDecision;
import com.ssafy.bidlive.auction.domain.VickreySettlementOutcome;
import com.ssafy.bidlive.auction.domain.VickreySettlementPolicy;
import com.ssafy.bidlive.deal.application.AuctionDealCreationService;
import com.ssafy.bidlive.prediction.application.PredictionSettlementCommand;
import com.ssafy.bidlive.prediction.application.PredictionSettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL 행 잠금 안에서 계산·포인트·Deal·결과·예측 정산을 한 번에 커밋한다. */
@Service
public class AuctionSettlementTransactionService {

	private final AuctionSettlementPersistence persistence;
	private final AuctionSettlementPointService pointService;
	private final AuctionDealCreationService dealCreationService;
	private final PredictionSettlementService predictionSettlementService;

	public AuctionSettlementTransactionService(
		AuctionSettlementPersistence persistence,
		AuctionSettlementPointService pointService,
		AuctionDealCreationService dealCreationService,
		PredictionSettlementService predictionSettlementService
	) {
		this.persistence = persistence;
		this.pointService = pointService;
		this.dealCreationService = dealCreationService;
		this.predictionSettlementService = predictionSettlementService;
	}

	@Transactional
	public AuctionSettlementResult settle(long auctionId, Instant settledAt) {
		SettlementTarget target = persistence.lockTarget(auctionId)
			.orElse(null);
		if (target == null) {
			return AuctionSettlementResult.skipped(
				auctionId,
				SKIPPED_NOT_FOUND
			);
		}
		if (target.resultExists()) {
			return storedResult(target);
		}
		if (!AuctionStatus.LIVE.name().equals(target.status())) {
			return AuctionSettlementResult.skipped(
				auctionId,
				SKIPPED_NOT_LIVE
			);
		}
		if (settledAt.isBefore(target.endAt())) {
			return AuctionSettlementResult.skipped(
				auctionId,
				SKIPPED_NOT_DUE
			);
		}

		List<FinalBid> finalBids = persistence.findFinalBids(auctionId);
		BidCounts counts = persistence.countBids(auctionId);
		if (counts.validBidderCount() != finalBids.size()) {
			throw new IllegalStateException(
				"final bidder count does not match persisted bid count"
			);
		}
		VickreySettlementDecision decision = VickreySettlementPolicy.calculate(
			target.reservePrice(),
			finalBids.stream().map(FinalBid::toVickreyBid).toList()
		);
		FinalBid winner = winner(finalBids, decision);

		pointService.settle(auctionId, decision);
		long auctionResultId = createResult(
			auctionId,
			decision,
			winner,
			counts
		);
		if (decision.outcome() == VickreySettlementOutcome.SOLD) {
			dealCreationService.create(auctionResultId);
		}
		if (persistence.markEnded(auctionId, settledAt) != 1) {
			throw new IllegalStateException(
				"live auction could not be marked ended"
			);
		}
		settlePredictions(auctionId, auctionResultId, decision);
		return completedResult(
			auctionId,
			target.reservePrice(),
			decision,
			winner,
			settledAt,
			true
		);
	}

	private AuctionSettlementResult storedResult(SettlementTarget target) {
		StoredResult stored = persistence.findStoredResult(target.auctionId())
			.orElseThrow(() -> new IllegalStateException(
				"auction result flag is true but result row is missing"
			));
		if (AuctionStatus.LIVE.name().equals(target.status())
			&& persistence.markEnded(target.auctionId(), stored.settledAt()) != 1) {
			throw new IllegalStateException(
				"stored auction result could not repair live room status"
			);
		}
		if (VickreySettlementOutcome.SOLD.name().equals(
			stored.resultStatus()
		)) {
			VickreySettlementDecision decision =
				VickreySettlementDecision.sold(
					stored.winnerMemberId(),
					stored.highestBidAmount(),
					stored.secondHighestBidAmount(),
					stored.finalPrice(),
					priceDecisionType(
						target.reservePrice(),
						stored.secondHighestBidAmount()
					)
				);
			return completedResult(
				target.auctionId(),
				target.reservePrice(),
				decision,
				stored.winnerNickname(),
				stored.settledAt(),
				false
			);
		}
		if (VickreySettlementOutcome.NO_SALE.name().equals(
			stored.resultStatus()
		)) {
			return completedResult(
				target.auctionId(),
				target.reservePrice(),
				VickreySettlementDecision.noSale(),
				(String) null,
				stored.settledAt(),
				false
			);
		}
		return AuctionSettlementResult.skipped(
			target.auctionId(),
			ALREADY_COMPLETED
		);
	}

	private long createResult(
		long auctionId,
		VickreySettlementDecision decision,
		FinalBid winner,
		BidCounts counts
	) {
		OptionalLong resultId = persistence.createResultIfAbsent(
			new ResultWrite(
				auctionId,
				decision.outcome(),
				winner == null ? null : winner.participantId(),
				winner == null ? null : winner.bidId(),
				decision.highestBidAmount(),
				decision.secondHighestBidAmount(),
				decision.finalPrice(),
				counts.validBidderCount(),
				counts.totalBidCount()
			)
		);
		if (resultId.isEmpty()) {
			throw new IllegalStateException(
				"auction result already exists after locked validation"
			);
		}
		return resultId.getAsLong();
	}

	private FinalBid winner(
		List<FinalBid> finalBids,
		VickreySettlementDecision decision
	) {
		if (decision.outcome() == VickreySettlementOutcome.NO_SALE) {
			return null;
		}
		return finalBids.stream()
			.filter(bid -> bid.memberId() == decision.winnerId())
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
				"vickrey winner is missing from final bids"
			));
	}

	private void settlePredictions(
		long auctionId,
		long auctionResultId,
		VickreySettlementDecision decision
	) {
		boolean sold = decision.outcome() == VickreySettlementOutcome.SOLD;
		predictionSettlementService.settle(new PredictionSettlementCommand(
			auctionId,
			auctionResultId,
			decision.finalPrice(),
			sold
				? PredictionSettlementCommand.CompletionType.SOLD
				: PredictionSettlementCommand.CompletionType.NO_BID
		));
	}

	private AuctionSettlementResult completedResult(
		long auctionId,
		long reservePrice,
		VickreySettlementDecision decision,
		FinalBid winner,
		Instant settledAt,
		boolean newlySettled
	) {
		return completedResult(
			auctionId,
			reservePrice,
			decision,
			winner == null ? null : winner.nickname(),
			settledAt,
			newlySettled
		);
	}

	private AuctionSettlementResult completedResult(
		long auctionId,
		long reservePrice,
		VickreySettlementDecision decision,
		String winnerNickname,
		Instant settledAt,
		boolean newlySettled
	) {
		boolean sold = decision.outcome() == VickreySettlementOutcome.SOLD;
		return new AuctionSettlementResult(
			auctionId,
			sold ? SETTLED : NO_SALE,
			newlySettled,
			decision.winnerId(),
			sold ? SellerNicknameMask.of(winnerNickname) : null,
			decision.highestBidAmount(),
			decision.secondHighestBidAmount(),
			reservePrice,
			decision.finalPrice(),
			decision.priceDecisionType(),
			settledAt
		);
	}

	private VickreyPriceDecisionType priceDecisionType(
		long reservePrice,
		Long secondHighestBidAmount
	) {
		return secondHighestBidAmount != null
			&& secondHighestBidAmount >= reservePrice
			? VickreyPriceDecisionType.SECOND_PRICE
			: VickreyPriceDecisionType.RESERVE_PRICE;
	}
}
