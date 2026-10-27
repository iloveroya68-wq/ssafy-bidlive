package com.ssafy.bidlive.prediction.application;

import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionSettlementPersistence.NewSettlement;
import com.ssafy.bidlive.prediction.application.PredictionSettlementPersistence.SaveResult;
import com.ssafy.bidlive.prediction.application.PredictionSettlementPersistence.SettlementTarget;
import com.ssafy.bidlive.prediction.domain.PredictionResultStatus;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementDecision;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementPolicy;
import com.ssafy.bidlive.wallet.application.BettingPointSettlementCommand;
import com.ssafy.bidlive.wallet.application.BettingPointSettlementService;
import com.ssafy.bidlive.wallet.domain.BettingPointSettlementType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 경매별 예측 결과·지갑·업적 반영 후 정산 완료 이벤트 등록을 조율한다. */
@Service
public class PredictionSettlementService {

	private static final String IDEMPOTENCY_KEY_PREFIX =
		"PREDICTION_SETTLEMENT:";

	private final PredictionSettlementPersistence persistence;
	private final BettingPointSettlementService walletSettlementService;
	private final PredictionAchievementSettlementService achievementService;
	private final ApplicationEventPublisher eventPublisher;
	private final PredictionSettlementPolicy policy;

	public PredictionSettlementService(
		PredictionSettlementPersistence persistence,
		BettingPointSettlementService walletSettlementService,
		PredictionAchievementSettlementService achievementService,
		ApplicationEventPublisher eventPublisher
	) {
		this.persistence = persistence;
		this.walletSettlementService = walletSettlementService;
		this.achievementService = achievementService;
		this.eventPublisher = eventPublisher;
		this.policy = new PredictionSettlementPolicy();
	}

	@Transactional
	public PredictionSettlementResult settle(
		PredictionSettlementCommand command
	) {
		if (command == null) {
			throw new IllegalArgumentException("command must not be null");
		}

		List<SettlementTarget> targets =
			persistence.findUnsettledTargets(command.auctionId());
		int settledCount = 0;
		int skippedCount = 0;
		long totalStakedPoints = 0L;
		long totalPayoutPoints = 0L;

		for (SettlementTarget target : targets) {
			PredictionSettlementDecision decision = decision(command, target);
			SaveResult saved = persistence.saveIfAbsent(new NewSettlement(
				target.predictionId(),
				command.auctionResultId(),
				decision
			));
			if (!saved.created()) {
				skippedCount++;
				continue;
			}

			settledCount++;
			totalStakedPoints = Math.addExact(
				totalStakedPoints,
				target.stakeAmount()
			);
			totalPayoutPoints = Math.addExact(
				totalPayoutPoints,
				decision.payoutAmount()
			);
			settleWallet(target, saved);
			settleAchievement(target, saved);
		}

		PredictionSettlementResult result = new PredictionSettlementResult(
			command.auctionId(),
			targets.size(),
			settledCount,
			skippedCount,
			totalStakedPoints,
			totalPayoutPoints
		);
		eventPublisher.publishEvent(
			new PredictionSettlementCompletedEvent(command.auctionId())
		);
		return result;
	}

	private PredictionSettlementDecision decision(
		PredictionSettlementCommand command,
		SettlementTarget target
	) {
		if (command.refunded()) {
			return policy.refund(target.stakeAmount());
		}
		return policy.settle(
			target.predictedPrice(),
			command.finalPrice(),
			target.stakeAmount()
		);
	}

	private void settleWallet(
		SettlementTarget target,
		SaveResult saved
	) {
		PredictionSettlementDecision decision =
			saved.settlement().decision();
		if (decision.payoutAmount() == 0) {
			return;
		}

		walletSettlementService.settle(
			new BettingPointSettlementCommand(
				target.memberId(),
				target.predictionId(),
				saved.settlement().predictionResultId(),
				walletSettlementType(decision),
				decision.payoutAmount(),
				IDEMPOTENCY_KEY_PREFIX + target.predictionId()
			)
		);
	}

	private BettingPointSettlementType walletSettlementType(
		PredictionSettlementDecision decision
	) {
		return decision.resultStatus() == PredictionResultStatus.REFUNDED
			? BettingPointSettlementType.REFUND
			: BettingPointSettlementType.PAYOUT;
	}

	private void settleAchievement(
		SettlementTarget target,
		SaveResult saved
	) {
		PredictionSettlementDecision decision =
			saved.settlement().decision();
		if (decision.resultStatus() == PredictionResultStatus.REFUNDED) {
			return;
		}
		achievementService.apply(
			target.memberId(),
			saved.settlement().predictionResultId(),
			decision
		);
	}
}
