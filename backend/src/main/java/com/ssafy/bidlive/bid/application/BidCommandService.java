package com.ssafy.bidlive.bid.application;

import static com.ssafy.bidlive.bid.domain.BidFailureCode.IDEMPOTENCY_KEY_REUSED;
import static com.ssafy.bidlive.bid.domain.BidFailureCode.INSUFFICIENT_AVAILABLE_BALANCE;
import static com.ssafy.bidlive.bid.domain.BidFailureCode.PREDICTION_ALREADY_JOINED;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import com.ssafy.bidlive.bid.application.BidPersistence.NewBid;
import com.ssafy.bidlive.bid.application.BidPersistence.StoredBid;
import com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode;
import com.ssafy.bidlive.wallet.application.TradePointOperationException;
import com.ssafy.bidlive.wallet.application.TradePointOperationResult;
import com.ssafy.bidlive.wallet.application.TradePointOperationService;
import com.ssafy.bidlive.wallet.application.TradeWalletRealtimeInitializationService;
import com.ssafy.bidlive.wallet.application.WalletQueryResult;
import com.ssafy.bidlive.wallet.application.WalletQueryService;
import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Redis 승인 입찰을 DB·지갑에 반영하고 공개 집계값 이벤트를 발행한다. */
@Service
public class BidCommandService {

	private static final String HOLD_IDEMPOTENCY_KEY_PREFIX = "BID_HOLD:";

	private final BidProcessor bidProcessor;
	private final BidPersistence persistence;
	private final TradePointOperationService tradePointOperationService;
	private final TradeWalletRealtimeInitializationService
		tradeWalletRealtimeInitializationService;
	private final WalletQueryService walletQueryService;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public BidCommandService(
		BidProcessor bidProcessor,
		BidPersistence persistence,
		TradePointOperationService tradePointOperationService,
		TradeWalletRealtimeInitializationService
			tradeWalletRealtimeInitializationService,
		WalletQueryService walletQueryService,
		ApplicationEventPublisher eventPublisher
	) {
		this(
			bidProcessor,
			persistence,
			tradePointOperationService,
			tradeWalletRealtimeInitializationService,
			walletQueryService,
			eventPublisher,
			Clock.systemDefaultZone()
		);
	}

	BidCommandService(
		BidProcessor bidProcessor,
		BidPersistence persistence,
		TradePointOperationService tradePointOperationService,
		TradeWalletRealtimeInitializationService
			tradeWalletRealtimeInitializationService,
		WalletQueryService walletQueryService,
		ApplicationEventPublisher eventPublisher,
		Clock clock
	) {
		this.bidProcessor = bidProcessor;
		this.persistence = persistence;
		this.tradePointOperationService = tradePointOperationService;
		this.tradeWalletRealtimeInitializationService =
			tradeWalletRealtimeInitializationService;
		this.walletQueryService = walletQueryService;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public BidResult createBid(
		Long auctionId,
		Long memberId,
		Long amount,
		UUID idempotencyKey
	) {
		if (idempotencyKey == null) {
			throw new IllegalArgumentException("idempotencyKey must not be null");
		}

		BidCommand command = new BidCommand(
			auctionId,
			memberId,
			amount,
			idempotencyKey.toString(),
			OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)
		);
		persistence.lockIdempotencyKey(command.requestId());

		StoredBid existing = persistence
			.findByIdempotencyKey(command.requestId())
			.orElse(null);
		if (existing != null) {
			return duplicatedResult(command, existing);
		}

		persistence.lockParticipation(auctionId, memberId);
		if (persistence.hasPredictionParticipation(auctionId, memberId)) {
			throw new BidRejectedException(PREDICTION_ALREADY_JOINED);
		}

		tradeWalletRealtimeInitializationService.initializeIfAbsent(
			memberId
		);
		BidProcessingResult processingResult = bidProcessor.process(command);
		if (!processingResult.accepted()) {
			throw new BidRejectedException(processingResult.failureCode());
		}

		StoredBid stored = persistence.save(new NewBid(
			auctionId,
			memberId,
			amount,
			command.requestId(),
			processingResult.submittedAt()
		));
		verifyStoredHistory(processingResult, stored);
		TradePointOperationResult wallet = holdTradePoints(
			command,
			stored,
			processingResult.additionalHoldAmount()
		);
		eventPublisher.publishEvent(new BidAcceptedEvent(
			auctionId,
			processingResult.bidCount(),
			processingResult.participantCount()
		));

		return new BidResult(
			auctionId,
			stored.amount(),
			stored.previousBidAmount(),
			stored.additionalHoldAmount(),
			wallet.heldAmount(),
			wallet.availableAmount(),
			stored.submittedAt()
		);
	}

	private BidResult duplicatedResult(
		BidCommand command,
		StoredBid existing
	) {
		if (!sameRequest(command, existing)) {
			throw new BidRejectedException(IDEMPOTENCY_KEY_REUSED);
		}
		WalletQueryResult.WalletSummary wallet = walletQueryService
			.query(command.memberId())
			.tradeWallet();
		return new BidResult(
			existing.auctionId(),
			existing.amount(),
			existing.previousBidAmount(),
			existing.additionalHoldAmount(),
			wallet.heldAmount(),
			wallet.availableAmount(),
			existing.submittedAt()
		);
	}

	private boolean sameRequest(BidCommand command, StoredBid existing) {
		return Objects.equals(command.auctionId(), existing.auctionId())
			&& Objects.equals(command.memberId(), existing.memberId())
			&& Objects.equals(command.amount(), existing.amount());
	}

	private void verifyStoredHistory(
		BidProcessingResult processingResult,
		StoredBid stored
	) {
		if (!Objects.equals(
			processingResult.previousBidAmount(),
			stored.previousBidAmount()
		)) {
			throw new IllegalStateException(
				"Redis and PostgreSQL previous bid amounts do not match"
			);
		}
	}

	private TradePointOperationResult holdTradePoints(
		BidCommand command,
		StoredBid stored,
		long additionalHoldAmount
	) {
		try {
			return tradePointOperationService.hold(
				PointLedgerReferenceType.BID,
				stored.bidId(),
				command.memberId(),
				additionalHoldAmount,
				HOLD_IDEMPOTENCY_KEY_PREFIX + command.requestId()
			);
		} catch (TradePointOperationException exception) {
			if (exception.getErrorCode()
				== TradePointOperationErrorCode.INSUFFICIENT_AVAILABLE_BALANCE) {
				throw new BidRejectedException(
					INSUFFICIENT_AVAILABLE_BALANCE,
					exception
				);
			}
			if (exception.getErrorCode()
				== TradePointOperationErrorCode.DUPLICATE_LEDGER_REFERENCE) {
				throw new BidRejectedException(
					IDEMPOTENCY_KEY_REUSED,
					exception
				);
			}
			throw new IllegalStateException(
				"TRADE wallet could not hold points for an accepted bid",
				exception
			);
		}
	}
}
