package com.ssafy.bidlive.wallet.application;

import java.util.Objects;

import com.ssafy.bidlive.wallet.application.BettingPointSettlementPersistence.LockedWallet;
import com.ssafy.bidlive.wallet.application.BettingPointSettlementPersistence.NewSettlement;
import com.ssafy.bidlive.wallet.application.BettingPointSettlementPersistence.SettlementRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 예측 배당·환불을 BETTING 지갑 잔액과 불변 원장에 원자적으로 반영한다. */
@Service
public class BettingPointSettlementService {

	private final BettingPointSettlementPersistence persistence;
	private final ApplicationEventPublisher eventPublisher;

	public BettingPointSettlementService(
		BettingPointSettlementPersistence persistence,
		ApplicationEventPublisher eventPublisher
	) {
		this.persistence = persistence;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public BettingPointSettlementResult settle(
		BettingPointSettlementCommand command
	) {
		if (command == null) {
			throw new IllegalArgumentException("command must not be null");
		}

		return persistence.findByIdempotencyKey(command.idempotencyKey())
			.map(existing -> duplicatedResult(command, existing))
			.orElseGet(() -> settleNew(command));
	}

	private BettingPointSettlementResult settleNew(
		BettingPointSettlementCommand command
	) {
		LockedWallet wallet = persistence.lockBettingWallet(command.memberId())
			.orElseThrow(() -> new BettingPointSettlementException(
				BettingPointSettlementErrorCode.BETTING_WALLET_NOT_FOUND
			));

		return persistence.findByIdempotencyKey(command.idempotencyKey())
			.map(existing -> duplicatedResult(command, existing))
			.orElseGet(() -> save(command, wallet));
	}

	private BettingPointSettlementResult save(
		BettingPointSettlementCommand command,
		LockedWallet wallet
	) {
		long availableBalanceAfter;
		try {
			availableBalanceAfter = Math.addExact(
				wallet.availableBalance(),
				command.amount()
			);
		} catch (ArithmeticException exception) {
			throw new BettingPointSettlementException(
				BettingPointSettlementErrorCode.BETTING_POINT_BALANCE_OVERFLOW,
				exception
			);
		}

		SettlementRecord saved = persistence.save(new NewSettlement(
			wallet.walletId(),
			wallet.memberId(),
			command.predictionId(),
			command.predictionResultId(),
			command.settlementType(),
			command.amount(),
			availableBalanceAfter,
			wallet.heldBalance(),
			command.idempotencyKey()
		));
		eventPublisher.publishEvent(new BettingWalletBalanceChangedEvent(
			saved.memberId(),
			saved.amount()
		));
		return result(saved, false);
	}

	private BettingPointSettlementResult duplicatedResult(
		BettingPointSettlementCommand command,
		SettlementRecord existing
	) {
		if (!sameRequest(command, existing)) {
			throw new BettingPointSettlementException(
				BettingPointSettlementErrorCode.IDEMPOTENCY_KEY_REUSED
			);
		}
		return result(existing, true);
	}

	private boolean sameRequest(
		BettingPointSettlementCommand command,
		SettlementRecord existing
	) {
		return Objects.equals(command.memberId(), existing.memberId())
			&& Objects.equals(command.predictionId(), existing.predictionId())
			&& Objects.equals(
				command.predictionResultId(),
				existing.predictionResultId()
			)
			&& command.settlementType() == existing.settlementType()
			&& command.amount() == existing.amount();
	}

	private BettingPointSettlementResult result(
		SettlementRecord settlement,
		boolean duplicated
	) {
		return new BettingPointSettlementResult(
			settlement.walletId(),
			settlement.ledgerId(),
			settlement.settlementType(),
			settlement.amount(),
			settlement.availableBalanceAfter(),
			duplicated
		);
	}
}
