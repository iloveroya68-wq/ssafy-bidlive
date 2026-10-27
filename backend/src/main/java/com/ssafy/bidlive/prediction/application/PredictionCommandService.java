package com.ssafy.bidlive.prediction.application;

import static com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode.IDEMPOTENCY_KEY_REUSED;
import static com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode.INSUFFICIENT_BETTING_POINTS;
import static com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode.PREDICTION_ALREADY_EXISTS;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import com.ssafy.bidlive.prediction.application.PredictionParticipationPersistence.NewPrediction;
import com.ssafy.bidlive.prediction.application.PredictionParticipationPersistence.StoredPrediction;
import com.ssafy.bidlive.wallet.application.BettingPointBetCommand;
import com.ssafy.bidlive.wallet.application.BettingPointBetErrorCode;
import com.ssafy.bidlive.wallet.application.BettingPointBetException;
import com.ssafy.bidlive.wallet.application.BettingPointBetService;
import com.ssafy.bidlive.wallet.application.BettingWalletRealtimeInitializationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 승인 예측을 원장에 반영하고 신규 참여의 공개 집계 이벤트를 발행한다. */
@Service
public class PredictionCommandService {

	private static final String BET_IDEMPOTENCY_KEY_PREFIX = "PREDICTION_BET:";

	private final PredictionParticipationProcessor participationProcessor;
	private final PredictionParticipationPersistence persistence;
	private final BettingPointBetService bettingPointBetService;
	private final BettingWalletRealtimeInitializationService
		bettingWalletRealtimeInitializationService;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public PredictionCommandService(
		PredictionParticipationProcessor participationProcessor,
		PredictionParticipationPersistence persistence,
		BettingPointBetService bettingPointBetService,
		BettingWalletRealtimeInitializationService
			bettingWalletRealtimeInitializationService,
		ApplicationEventPublisher eventPublisher
	) {
		this(
			participationProcessor,
			persistence,
			bettingPointBetService,
			bettingWalletRealtimeInitializationService,
			eventPublisher,
			Clock.systemDefaultZone()
		);
	}

	PredictionCommandService(
		PredictionParticipationProcessor participationProcessor,
		PredictionParticipationPersistence persistence,
		BettingPointBetService bettingPointBetService,
		BettingWalletRealtimeInitializationService
			bettingWalletRealtimeInitializationService,
		ApplicationEventPublisher eventPublisher,
		Clock clock
	) {
		this.participationProcessor = participationProcessor;
		this.persistence = persistence;
		this.bettingPointBetService = bettingPointBetService;
		this.bettingWalletRealtimeInitializationService =
			bettingWalletRealtimeInitializationService;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public PredictionCreateResult createPrediction(
		Long auctionId,
		Long memberId,
		Long predictedPrice,
		Long stakeAmount,
		UUID clientRequestId
	) {
		if (clientRequestId == null) {
			throw new IllegalArgumentException(
				"clientRequestId must not be null"
			);
		}

		OffsetDateTime requestedAt = OffsetDateTime.now(clock);
		PredictionParticipationCommand command =
			new PredictionParticipationCommand(
				auctionId,
				memberId,
				predictedPrice,
				stakeAmount,
				clientRequestId,
				requestedAt
			);
		String idempotencyKey = clientRequestId.toString();
		persistence.lockIdempotencyKey(idempotencyKey);

		return persistence.findByIdempotencyKey(idempotencyKey)
			.map(existing -> duplicatedResult(command, existing))
			.orElseGet(() -> createNew(command));
	}

	private PredictionCreateResult createNew(
		PredictionParticipationCommand command
	) {
		if (persistence.findByAuctionIdAndMemberId(
			command.auctionId(),
			command.memberId()
		).isPresent()) {
			throw new PredictionRejectedException(
				PREDICTION_ALREADY_EXISTS
			);
		}

		bettingWalletRealtimeInitializationService.initializeIfAbsent(
			command.memberId()
		);
		PredictionParticipationResult participation =
			participationProcessor.process(command);
		if (!participation.accepted()) {
			throw new PredictionRejectedException(
				participation.failureCode()
			);
		}

		StoredPrediction stored = persistence.save(new NewPrediction(
			command.auctionId(),
			command.memberId(),
			command.predictedPrice(),
			command.stakeAmount(),
			command.clientRequestId().toString(),
			participation.submittedAt()
		));
		deductBettingPoints(command, stored);
		eventPublisher.publishEvent(new PredictionAcceptedEvent(
			command.auctionId(),
			participation.participantCount()
		));
		return result(stored);
	}

	private PredictionCreateResult duplicatedResult(
		PredictionParticipationCommand command,
		StoredPrediction existing
	) {
		if (!sameRequest(command, existing)) {
			throw new PredictionRejectedException(IDEMPOTENCY_KEY_REUSED);
		}
		return result(existing);
	}

	private boolean sameRequest(
		PredictionParticipationCommand command,
		StoredPrediction existing
	) {
		return Objects.equals(command.auctionId(), existing.auctionId())
			&& Objects.equals(command.memberId(), existing.memberId())
			&& Objects.equals(
				command.predictedPrice(),
				existing.predictedPrice()
			)
			&& Objects.equals(command.stakeAmount(), existing.stakeAmount());
	}

	private void deductBettingPoints(
		PredictionParticipationCommand command,
		StoredPrediction stored
	) {
		try {
			bettingPointBetService.deduct(new BettingPointBetCommand(
				command.memberId(),
				stored.predictionId(),
				command.stakeAmount(),
				BET_IDEMPOTENCY_KEY_PREFIX + command.clientRequestId()
			));
		} catch (BettingPointBetException exception) {
			throw walletFailure(exception);
		}
	}

	private RuntimeException walletFailure(
		BettingPointBetException exception
	) {
		if (exception.getErrorCode()
			== BettingPointBetErrorCode.INSUFFICIENT_BETTING_POINTS) {
			return new PredictionRejectedException(
				INSUFFICIENT_BETTING_POINTS,
				exception
			);
		}
		if (exception.getErrorCode()
			== BettingPointBetErrorCode.IDEMPOTENCY_KEY_REUSED) {
			return new PredictionRejectedException(
				IDEMPOTENCY_KEY_REUSED,
				exception
			);
		}
		return new IllegalStateException(
			"BETTING wallet was not found for an accepted prediction",
			exception
		);
	}

	private PredictionCreateResult result(StoredPrediction stored) {
		return new PredictionCreateResult(
			stored.predictionId(),
			stored.auctionId(),
			stored.predictedPrice(),
			stored.stakeAmount(),
			stored.submittedAt()
		);
	}
}
