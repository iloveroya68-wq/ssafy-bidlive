package com.ssafy.bidlive.wallet.application;

import java.util.Objects;

import com.ssafy.bidlive.wallet.application.BettingPointBetPersistence.LedgerRecord;
import com.ssafy.bidlive.wallet.application.BettingPointBetPersistence.LockedWallet;
import com.ssafy.bidlive.wallet.application.BettingPointBetPersistence.NewBet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 예측 스테이크를 BETTING 지갑과 BET 불변 원장에 원자적으로 반영한다. */
@Service
public class BettingPointBetService {

	private final BettingPointBetPersistence persistence;

	public BettingPointBetService(BettingPointBetPersistence persistence) {
		this.persistence = persistence;
	}

	@Transactional
	public BettingPointBetResult deduct(BettingPointBetCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("command must not be null");
		}

		return persistence.findByIdempotencyKey(command.idempotencyKey())
			.map(existing -> duplicatedResult(command, existing))
			.orElseGet(() -> deductNew(command));
	}

	private BettingPointBetResult deductNew(BettingPointBetCommand command) {
		LockedWallet wallet = persistence.lockBettingWallet(command.memberId())
			.orElseThrow(() -> new BettingPointBetException(
				BettingPointBetErrorCode.BETTING_WALLET_NOT_FOUND
			));

		return persistence.findByIdempotencyKey(command.idempotencyKey())
			.map(existing -> duplicatedResult(command, existing))
			.orElseGet(() -> save(command, wallet));
	}

	private BettingPointBetResult save(
		BettingPointBetCommand command,
		LockedWallet wallet
	) {
		if (wallet.availableBalance() < command.amount()) {
			throw new BettingPointBetException(
				BettingPointBetErrorCode.INSUFFICIENT_BETTING_POINTS
			);
		}

		long availableBalanceAfter =
			wallet.availableBalance() - command.amount();
		LedgerRecord saved = persistence.save(new NewBet(
			wallet.walletId(),
			wallet.memberId(),
			command.predictionId(),
			command.amount(),
			availableBalanceAfter,
			wallet.heldBalance(),
			command.idempotencyKey()
		));
		return result(saved, false);
	}

	private BettingPointBetResult duplicatedResult(
		BettingPointBetCommand command,
		LedgerRecord existing
	) {
		if (!sameRequest(command, existing)) {
			throw new BettingPointBetException(
				BettingPointBetErrorCode.IDEMPOTENCY_KEY_REUSED
			);
		}
		return result(existing, true);
	}

	private boolean sameRequest(
		BettingPointBetCommand command,
		LedgerRecord existing
	) {
		return existing.betTransaction()
			&& Objects.equals(command.memberId(), existing.memberId())
			&& Objects.equals(command.predictionId(), existing.predictionId())
			&& existing.availableDelta() == -command.amount();
	}

	private BettingPointBetResult result(
		LedgerRecord bet,
		boolean duplicated
	) {
		return new BettingPointBetResult(
			bet.walletId(),
			bet.ledgerId(),
			bet.predictionId(),
			-bet.availableDelta(),
			bet.availableBalanceAfter(),
			duplicated
		);
	}
}
