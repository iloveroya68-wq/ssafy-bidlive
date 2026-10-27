package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.TradePointChargeErrorCode.CHARGE_LIMIT_EXCEEDED;
import static com.ssafy.bidlive.wallet.application.TradePointChargeErrorCode.DUPLICATE_REQUEST;
import static com.ssafy.bidlive.wallet.application.TradePointChargeErrorCode.INVALID_CHARGE_AMOUNT;
import static com.ssafy.bidlive.wallet.application.TradePointChargeErrorCode.TRADE_WALLET_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.TradePointChargeErrorCode.UNAUTHORIZED;

import java.time.Clock;
import java.time.Instant;

import com.ssafy.bidlive.wallet.application.TradePointChargePersistence.ChargeLedgerRecord;
import com.ssafy.bidlive.wallet.application.TradePointChargePersistence.LockedTradeWallet;
import com.ssafy.bidlive.wallet.application.TradePointChargePersistence.NewCharge;
import com.ssafy.bidlive.wallet.domain.TradePointChargePolicy;
import com.ssafy.bidlive.wallet.domain.TradePointChargePolicy.DailyWindow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 거래 포인트 충전을 DB에 반영하고 커밋 후 Redis 동기화 이벤트를 발행한다. */
@Service
public class TradePointChargeService {

	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

	private final TradePointChargePersistence persistence;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public TradePointChargeService(
		TradePointChargePersistence persistence,
		ApplicationEventPublisher eventPublisher
	) {
		this(
			persistence,
			eventPublisher,
			Clock.system(TradePointChargePolicy.DAILY_LIMIT_ZONE)
		);
	}

	TradePointChargeService(
		TradePointChargePersistence persistence,
		ApplicationEventPublisher eventPublisher,
		Clock clock
	) {
		this.persistence = persistence;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public TradePointChargeResult charge(
		TradePointChargeCommand command
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
		validateDailyLimit(wallet.walletId(), command.amount());

		long availableBalanceAfter;
		long balanceAfter;
		try {
			availableBalanceAfter = Math.addExact(
				wallet.availableBalance(),
				command.amount()
			);
			balanceAfter = Math.addExact(
				availableBalanceAfter,
				wallet.heldBalance()
			);
		} catch (ArithmeticException exception) {
			throw error(CHARGE_LIMIT_EXCEEDED);
		}

		ChargeLedgerRecord ledger = persistence.save(new NewCharge(
			wallet.walletId(),
			command.amount(),
			availableBalanceAfter,
			wallet.heldBalance(),
			command.idempotencyKey()
		));
		TradePointChargeResult result = new TradePointChargeResult(
			command.amount(),
			balanceAfter,
			ledger.heldBalanceAfter(),
			ledger.availableBalanceAfter(),
			ledger.ledgerId(),
			ledger.chargedAt()
		);
		eventPublisher.publishEvent(new TradeWalletBalanceChangedEvent(
			command.memberId(),
			ledger.ledgerId(),
			command.amount(),
			0L,
			ledger.availableBalanceAfter(),
			ledger.heldBalanceAfter()
		));
		return result;
	}

	private void validate(TradePointChargeCommand command) {
		if (command == null
			|| command.memberId() == null
			|| command.memberId() <= 0) {
			throw error(UNAUTHORIZED);
		}
		if (!TradePointChargePolicy.isPositive(command.amount())) {
			throw error(INVALID_CHARGE_AMOUNT);
		}
		if (TradePointChargePolicy.exceedsSingleChargeLimit(
			command.amount()
		)) {
			throw error(CHARGE_LIMIT_EXCEEDED);
		}
		if (command.idempotencyKey() == null
			|| command.idempotencyKey().isBlank()
			|| command.idempotencyKey().length()
				> MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw error(INVALID_CHARGE_AMOUNT);
		}
	}

	private void validateDailyLimit(Long walletId, long amount) {
		Instant now = clock.instant();
		DailyWindow window = TradePointChargePolicy.dailyWindow(now);
		long chargedAmountToday = persistence.sumChargedAmount(
			walletId,
			window.startInclusive(),
			window.endExclusive()
		);
		if (TradePointChargePolicy.exceedsDailyChargeLimit(
			chargedAmountToday,
			amount
		)) {
			throw error(CHARGE_LIMIT_EXCEEDED);
		}
	}

	private TradePointChargeException error(
		TradePointChargeErrorCode errorCode
	) {
		return new TradePointChargeException(errorCode);
	}
}
