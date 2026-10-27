package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.DUPLICATE_LEDGER_REFERENCE;
import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.HOLD_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.INSUFFICIENT_AVAILABLE_BALANCE;
import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.INVALID_HOLD_AMOUNT;
import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.INVALID_RELEASE_AMOUNT;
import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.INVALID_SETTLEMENT_AMOUNT;
import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.WALLET_NOT_FOUND;
import static com.ssafy.bidlive.wallet.domain.TradePointOperationType.HOLD;
import static com.ssafy.bidlive.wallet.domain.TradePointOperationType.RELEASE;
import static com.ssafy.bidlive.wallet.domain.TradePointOperationType.SETTLEMENT;

import com.ssafy.bidlive.wallet.application.TradePointOperationPersistence.LockedTradeWallet;
import com.ssafy.bidlive.wallet.application.TradePointOperationPersistence.NewOperation;
import com.ssafy.bidlive.wallet.application.TradePointOperationPersistence.OperationLedgerRecord;
import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import com.ssafy.bidlive.wallet.domain.TradePointOperationType;
import com.ssafy.bidlive.wallet.domain.WalletType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 거래 포인트 작업을 DB에 반영하고 해제·정산의 Redis 동기화를 예약한다. */
@Service
public class TradePointOperationService {

	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

	private final TradePointOperationPersistence persistence;
	private final ApplicationEventPublisher eventPublisher;

	public TradePointOperationService(
		TradePointOperationPersistence persistence,
		ApplicationEventPublisher eventPublisher
	) {
		this.persistence = persistence;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public TradePointOperationResult hold(
		PointLedgerReferenceType referenceType,
		Long referenceId,
		Long memberId,
		long amount,
		String idempotencyKey
	) {
		return execute(
			HOLD,
			new TradePointOperationCommand(
				referenceType,
				referenceId,
				memberId,
				amount,
				idempotencyKey
			)
		);
	}

	@Transactional
	public TradePointOperationResult release(
		PointLedgerReferenceType referenceType,
		Long referenceId,
		Long memberId,
		long amount,
		String idempotencyKey
	) {
		return execute(
			RELEASE,
			new TradePointOperationCommand(
				referenceType,
				referenceId,
				memberId,
				amount,
				idempotencyKey
			)
		);
	}

	@Transactional
	public TradePointOperationResult settle(
		PointLedgerReferenceType referenceType,
		Long referenceId,
		Long memberId,
		long amount,
		String idempotencyKey
	) {
		return execute(
			SETTLEMENT,
			new TradePointOperationCommand(
				referenceType,
				referenceId,
				memberId,
				amount,
				idempotencyKey
			)
		);
	}

	private TradePointOperationResult execute(
		TradePointOperationType operationType,
		TradePointOperationCommand command
	) {
		validate(operationType, command);

		persistence.lockIdempotencyKey(command.idempotencyKey());
		if (persistence.existsByIdempotencyKey(
			command.idempotencyKey()
		)) {
			throw error(DUPLICATE_LEDGER_REFERENCE);
		}

		LockedTradeWallet wallet = persistence
			.lockTradeWallet(command.memberId())
			.orElseThrow(() -> error(WALLET_NOT_FOUND));
		if (persistence.existsByReferenceAndOperation(
			wallet.walletId(),
			command.referenceType(),
			command.referenceId(),
			operationType
		)) {
			throw error(DUPLICATE_LEDGER_REFERENCE);
		}

		long remainingHold = operationType == HOLD
			? 0
			: persistence.findRemainingHold(
				wallet.walletId(),
				command.referenceType(),
				command.referenceId()
			);
		NewOperation operation = calculate(
			operationType,
			command,
			wallet,
			remainingHold
		);

		try {
			OperationLedgerRecord ledger = persistence.save(operation);
			TradePointOperationResult result = result(operation, ledger);
			if (operationType != HOLD) {
				eventPublisher.publishEvent(
					new TradeWalletBalanceChangedEvent(
						command.memberId(),
						ledger.ledgerId(),
						operation.availableDelta(),
						operation.heldDelta(),
						ledger.availableBalanceAfter(),
						ledger.heldBalanceAfter()
					)
				);
			}
			return result;
		} catch (DataIntegrityViolationException exception) {
			throw new TradePointOperationException(
				DUPLICATE_LEDGER_REFERENCE,
				exception
			);
		}
	}

	private NewOperation calculate(
		TradePointOperationType operationType,
		TradePointOperationCommand command,
		LockedTradeWallet wallet,
		long remainingHold
	) {
		return switch (operationType) {
			case HOLD -> calculateHold(command, wallet);
			case RELEASE -> calculateRelease(
				command,
				wallet,
				remainingHold
			);
			case SETTLEMENT -> calculateSettlement(
				command,
				wallet,
				remainingHold
			);
		};
	}

	private NewOperation calculateHold(
		TradePointOperationCommand command,
		LockedTradeWallet wallet
	) {
		if (command.amount() > wallet.availableBalance()) {
			throw error(INSUFFICIENT_AVAILABLE_BALANCE);
		}
		try {
			return operation(
				command,
				HOLD,
				Math.subtractExact(
					wallet.availableBalance(),
					command.amount()
				),
				Math.addExact(
					wallet.heldBalance(),
					command.amount()
				),
				wallet.walletId()
			);
		} catch (ArithmeticException exception) {
			throw new TradePointOperationException(
				INVALID_HOLD_AMOUNT,
				exception
			);
		}
	}

	private NewOperation calculateRelease(
		TradePointOperationCommand command,
		LockedTradeWallet wallet,
		long remainingHold
	) {
		validateRemainingHold(
			remainingHold,
			command.amount(),
			wallet.heldBalance(),
			INVALID_RELEASE_AMOUNT
		);
		try {
			return operation(
				command,
				RELEASE,
				Math.addExact(
					wallet.availableBalance(),
					command.amount()
				),
				Math.subtractExact(
					wallet.heldBalance(),
					command.amount()
				),
				wallet.walletId()
			);
		} catch (ArithmeticException exception) {
			throw new TradePointOperationException(
				INVALID_RELEASE_AMOUNT,
				exception
			);
		}
	}

	private NewOperation calculateSettlement(
		TradePointOperationCommand command,
		LockedTradeWallet wallet,
		long remainingHold
	) {
		validateRemainingHold(
			remainingHold,
			command.amount(),
			wallet.heldBalance(),
			INVALID_SETTLEMENT_AMOUNT
		);
		try {
			return operation(
				command,
				SETTLEMENT,
				wallet.availableBalance(),
				Math.subtractExact(
					wallet.heldBalance(),
					command.amount()
				),
				wallet.walletId()
			);
		} catch (ArithmeticException exception) {
			throw new TradePointOperationException(
				INVALID_SETTLEMENT_AMOUNT,
				exception
			);
		}
	}

	private void validateRemainingHold(
		long remainingHold,
		long amount,
		long totalHeldBalance,
		TradePointOperationErrorCode invalidAmountError
	) {
		if (remainingHold <= 0) {
			throw error(HOLD_NOT_FOUND);
		}
		if (amount > remainingHold || amount > totalHeldBalance) {
			throw error(invalidAmountError);
		}
	}

	private NewOperation operation(
		TradePointOperationCommand command,
		TradePointOperationType operationType,
		long availableBalanceAfter,
		long heldBalanceAfter,
		Long walletId
	) {
		return new NewOperation(
			walletId,
			command.referenceType(),
			command.referenceId(),
			operationType,
			command.amount(),
			availableBalanceAfter,
			heldBalanceAfter,
			command.idempotencyKey()
		);
	}

	private TradePointOperationResult result(
		NewOperation operation,
		OperationLedgerRecord ledger
	) {
		long balance;
		try {
			balance = Math.addExact(
				ledger.availableBalanceAfter(),
				ledger.heldBalanceAfter()
			);
		} catch (ArithmeticException exception) {
			throw new IllegalStateException(
				"TRADE wallet total balance overflow",
				exception
			);
		}
		return new TradePointOperationResult(
			WalletType.TRADE,
			operation.operationType(),
			operation.amount(),
			balance,
			ledger.heldBalanceAfter(),
			ledger.availableBalanceAfter(),
			ledger.ledgerId()
		);
	}

	private void validate(
		TradePointOperationType operationType,
		TradePointOperationCommand command
	) {
		if (command == null
			|| command.referenceType() == null
			|| command.referenceId() == null
			|| command.referenceId() <= 0
			|| command.memberId() == null
			|| command.memberId() <= 0
			|| command.idempotencyKey() == null
			|| command.idempotencyKey().isBlank()
			|| !command.idempotencyKey().equals(
				command.idempotencyKey().trim()
			)
			|| command.idempotencyKey().length()
				> MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new IllegalArgumentException(
				"Invalid trade point operation command"
			);
		}
		if (command.amount() <= 0) {
			throw error(switch (operationType) {
				case HOLD -> INVALID_HOLD_AMOUNT;
				case RELEASE -> INVALID_RELEASE_AMOUNT;
				case SETTLEMENT -> INVALID_SETTLEMENT_AMOUNT;
			});
		}
	}

	private TradePointOperationException error(
		TradePointOperationErrorCode errorCode
	) {
		return new TradePointOperationException(errorCode);
	}
}
