package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.TradePointRefundErrorCode.DUPLICATE_REQUEST;
import static com.ssafy.bidlive.wallet.application.TradePointRefundErrorCode.INSUFFICIENT_AVAILABLE_AMOUNT;
import static com.ssafy.bidlive.wallet.application.TradePointRefundErrorCode.INVALID_REFUND_AMOUNT;
import static com.ssafy.bidlive.wallet.application.TradePointRefundErrorCode.TRADE_WALLET_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.TradePointRefundErrorCode.UNAUTHORIZED;

import java.util.UUID;

import com.ssafy.bidlive.wallet.application.TradePointRefundPersistence.LockedTradeWallet;
import com.ssafy.bidlive.wallet.application.TradePointRefundPersistence.NewRefund;
import com.ssafy.bidlive.wallet.application.TradePointRefundPersistence.RefundLedgerRecord;
import com.ssafy.bidlive.wallet.domain.TradePointRefundPolicy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 거래 포인트 환불을 DB에 반영하고 커밋 후 Redis 동기화 이벤트를 발행한다. */
@Service
public class TradePointRefundService {

	private final TradePointRefundPersistence persistence;
	private final ApplicationEventPublisher eventPublisher;

	public TradePointRefundService(
		TradePointRefundPersistence persistence,
		ApplicationEventPublisher eventPublisher
	) {
		this.persistence = persistence;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public TradePointRefundResult refund(
		TradePointRefundCommand command
	) {
		validate(command);

		persistence.lockIdempotencyKey(command.idempotencyKey());
		if (persistence.existsByIdempotencyKey(
			command.idempotencyKey()
		)) {
			throw error(DUPLICATE_REQUEST);
		}

		LockedTradeWallet wallet = persistence
			.lockTradeWallet(command.memberId())
			.orElseThrow(() -> error(TRADE_WALLET_NOT_FOUND));
		if (command.amount() > wallet.availableBalance()) {
			throw error(INSUFFICIENT_AVAILABLE_AMOUNT);
		}

		long availableBalanceAfter = Math.subtractExact(
			wallet.availableBalance(),
			command.amount()
		);
		long balanceAfter = Math.addExact(
			availableBalanceAfter,
			wallet.heldBalance()
		);
		RefundLedgerRecord ledger = persistence.save(new NewRefund(
			wallet.walletId(),
			command.amount(),
			availableBalanceAfter,
			wallet.heldBalance(),
			command.idempotencyKey()
		));
		TradePointRefundResult result = new TradePointRefundResult(
			command.amount(),
			balanceAfter,
			ledger.heldBalanceAfter(),
			ledger.availableBalanceAfter(),
			ledger.ledgerId(),
			ledger.refundedAt()
		);
		eventPublisher.publishEvent(new TradeWalletBalanceChangedEvent(
			command.memberId(),
			ledger.ledgerId(),
			-command.amount(),
			0L,
			ledger.availableBalanceAfter(),
			ledger.heldBalanceAfter()
		));
		return result;
	}

	private void validate(TradePointRefundCommand command) {
		if (command == null
			|| command.memberId() == null
			|| command.memberId() <= 0) {
			throw error(UNAUTHORIZED);
		}
		if (!TradePointRefundPolicy.isPositive(command.amount())
			|| !isUuid(command.idempotencyKey())) {
			throw error(INVALID_REFUND_AMOUNT);
		}
	}

	private boolean isUuid(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		try {
			String normalized = value.trim();
			return value.equals(normalized)
				&& UUID.fromString(normalized)
				.toString()
				.equalsIgnoreCase(normalized);
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private TradePointRefundException error(
		TradePointRefundErrorCode errorCode
	) {
		return new TradePointRefundException(errorCode);
	}
}
