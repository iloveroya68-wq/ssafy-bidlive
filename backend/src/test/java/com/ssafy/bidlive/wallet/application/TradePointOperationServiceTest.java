package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.DUPLICATE_LEDGER_REFERENCE;
import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.HOLD_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.INSUFFICIENT_AVAILABLE_BALANCE;
import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.INVALID_HOLD_AMOUNT;
import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.INVALID_RELEASE_AMOUNT;
import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.INVALID_SETTLEMENT_AMOUNT;
import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.WALLET_NOT_FOUND;
import static com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType.BID;
import static com.ssafy.bidlive.wallet.domain.TradePointOperationType.HOLD;
import static com.ssafy.bidlive.wallet.domain.TradePointOperationType.RELEASE;
import static com.ssafy.bidlive.wallet.domain.TradePointOperationType.SETTLEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.ssafy.bidlive.wallet.application.TradePointOperationPersistence.LockedTradeWallet;
import com.ssafy.bidlive.wallet.application.TradePointOperationPersistence.NewOperation;
import com.ssafy.bidlive.wallet.application.TradePointOperationPersistence.OperationLedgerRecord;
import com.ssafy.bidlive.wallet.domain.TradePointOperationType;
import com.ssafy.bidlive.wallet.domain.WalletType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** 홀드·해제·정산 계산과 해제·정산 동기화 이벤트를 검증한다. */
@ExtendWith(MockitoExtension.class)
class TradePointOperationServiceTest {

	private static final Long MEMBER_ID = 25L;
	private static final Long WALLET_ID = 10L;
	private static final Long REFERENCE_ID = 351L;
	private static final String IDEMPOTENCY_KEY = "BID:351:HOLD";

	@Mock
	private TradePointOperationPersistence persistence;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private TradePointOperationService service;

	@BeforeEach
	void setUp() {
		service = new TradePointOperationService(
			persistence,
			eventPublisher
		);
	}

	@Test
	void holdsAvailableBalanceAndCreatesImmutableLedger() {
		stubNewOperation(HOLD, 200_000L, 0L);
		when(persistence.save(any())).thenReturn(
			new OperationLedgerRecord(1001L, 118_000L, 82_000L)
		);

		TradePointOperationResult result = service.hold(
			BID,
			REFERENCE_ID,
			MEMBER_ID,
			82_000L,
			IDEMPOTENCY_KEY
		);

		assertThat(result).isEqualTo(new TradePointOperationResult(
			WalletType.TRADE,
			HOLD,
			82_000L,
			200_000L,
			82_000L,
			118_000L,
			1001L
		));
		assertSavedOperation(
			HOLD,
			82_000L,
			118_000L,
			82_000L,
			IDEMPOTENCY_KEY
		);
		verify(persistence, never()).findRemainingHold(any(), any(), any());
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void releasesOnlyTheHoldOwnedByTheReference() {
		String key = "BID:351:RELEASE";
		stubNewOperation(RELEASE, 118_000L, 82_000L);
		when(persistence.findRemainingHold(WALLET_ID, BID, REFERENCE_ID))
			.thenReturn(82_000L);
		when(persistence.save(any())).thenReturn(
			new OperationLedgerRecord(1002L, 138_000L, 62_000L)
		);

		TradePointOperationResult result = service.release(
			BID,
			REFERENCE_ID,
			MEMBER_ID,
			20_000L,
			key
		);

		assertThat(result.operationType()).isEqualTo(RELEASE);
		assertThat(result.balance()).isEqualTo(200_000L);
		assertThat(result.availableAmount()).isEqualTo(138_000L);
		assertThat(result.heldAmount()).isEqualTo(62_000L);
		assertSavedOperation(
			RELEASE,
			20_000L,
			138_000L,
			62_000L,
			key
		);
		verify(eventPublisher).publishEvent(
			new TradeWalletBalanceChangedEvent(
				MEMBER_ID,
				1002L,
				20_000L,
				-20_000L,
				138_000L,
				62_000L
			)
		);
	}

	@Test
	void settlesHeldBalanceByRemovingItFromTheWallet() {
		String key = "BID:351:SETTLEMENT";
		stubNewOperation(SETTLEMENT, 118_000L, 82_000L);
		when(persistence.findRemainingHold(WALLET_ID, BID, REFERENCE_ID))
			.thenReturn(82_000L);
		when(persistence.save(any())).thenReturn(
			new OperationLedgerRecord(1003L, 118_000L, 0L)
		);

		TradePointOperationResult result = service.settle(
			BID,
			REFERENCE_ID,
			MEMBER_ID,
			82_000L,
			key
		);

		assertThat(result.operationType()).isEqualTo(SETTLEMENT);
		assertThat(result.balance()).isEqualTo(118_000L);
		assertThat(result.availableAmount()).isEqualTo(118_000L);
		assertThat(result.heldAmount()).isZero();
		assertSavedOperation(
			SETTLEMENT,
			82_000L,
			118_000L,
			0L,
			key
		);
	}

	@Test
	void rejectsNonPositiveAmountWithOperationSpecificError() {
		assertError(
			() -> service.hold(
				BID,
				REFERENCE_ID,
				MEMBER_ID,
				0,
				IDEMPOTENCY_KEY
			),
			INVALID_HOLD_AMOUNT
		);
		assertError(
			() -> service.release(
				BID,
				REFERENCE_ID,
				MEMBER_ID,
				-1,
				"BID:351:RELEASE"
			),
			INVALID_RELEASE_AMOUNT
		);
		assertError(
			() -> service.settle(
				BID,
				REFERENCE_ID,
				MEMBER_ID,
				0,
				"BID:351:SETTLEMENT"
			),
			INVALID_SETTLEMENT_AMOUNT
		);

		verifyNoInteractions(persistence);
	}

	@Test
	void rejectsInvalidReferenceMemberAndIdempotencyKey() {
		assertThatIllegalArgumentException().isThrownBy(() -> service.hold(
			null,
			REFERENCE_ID,
			MEMBER_ID,
			100L,
			IDEMPOTENCY_KEY
		));
		assertThatIllegalArgumentException().isThrownBy(() -> service.hold(
			BID,
			0L,
			MEMBER_ID,
			100L,
			IDEMPOTENCY_KEY
		));
		assertThatIllegalArgumentException().isThrownBy(() -> service.hold(
			BID,
			REFERENCE_ID,
			null,
			100L,
			IDEMPOTENCY_KEY
		));
		assertThatIllegalArgumentException().isThrownBy(() -> service.hold(
			BID,
			REFERENCE_ID,
			MEMBER_ID,
			100L,
			" BID:351:HOLD"
		));

		verifyNoInteractions(persistence);
	}

	@Test
	void rejectsDuplicateIdempotencyKeyOrReferenceOperation() {
		when(persistence.existsByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(true);

		assertError(
			() -> service.hold(
				BID,
				REFERENCE_ID,
				MEMBER_ID,
				82_000L,
				IDEMPOTENCY_KEY
			),
			DUPLICATE_LEDGER_REFERENCE
		);
		verify(persistence, never()).lockTradeWallet(any());

		String differentKey = "BID:351:HOLD:RETRY";
		when(persistence.existsByIdempotencyKey(differentKey))
			.thenReturn(false);
		when(persistence.lockTradeWallet(MEMBER_ID))
			.thenReturn(Optional.of(wallet(200_000L, 0L)));
		when(persistence.existsByReferenceAndOperation(
			WALLET_ID,
			BID,
			REFERENCE_ID,
			HOLD
		)).thenReturn(true);

		assertError(
			() -> service.hold(
				BID,
				REFERENCE_ID,
				MEMBER_ID,
				82_000L,
				differentKey
			),
			DUPLICATE_LEDGER_REFERENCE
		);
		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsMissingWalletAndInsufficientAvailableBalance() {
		when(persistence.existsByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(false);
		when(persistence.lockTradeWallet(MEMBER_ID))
			.thenReturn(Optional.empty());

		assertError(
			() -> service.hold(
				BID,
				REFERENCE_ID,
				MEMBER_ID,
				82_000L,
				IDEMPOTENCY_KEY
			),
			WALLET_NOT_FOUND
		);

		when(persistence.lockTradeWallet(MEMBER_ID))
			.thenReturn(Optional.of(wallet(81_999L, 0L)));
		when(persistence.existsByReferenceAndOperation(
			WALLET_ID,
			BID,
			REFERENCE_ID,
			HOLD
		)).thenReturn(false);

		assertError(
			() -> service.hold(
				BID,
				REFERENCE_ID,
				MEMBER_ID,
				82_000L,
				IDEMPOTENCY_KEY
			),
			INSUFFICIENT_AVAILABLE_BALANCE
		);
		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsMissingOrInsufficientReferenceHold() {
		String releaseKey = "BID:351:RELEASE";
		stubNewOperation(RELEASE, 150_000L, 50_000L);
		when(persistence.findRemainingHold(WALLET_ID, BID, REFERENCE_ID))
			.thenReturn(0L);

		assertError(
			() -> service.release(
				BID,
				REFERENCE_ID,
				MEMBER_ID,
				10_000L,
				releaseKey
			),
			HOLD_NOT_FOUND
		);

		String settlementKey = "BID:351:SETTLEMENT";
		when(persistence.existsByIdempotencyKey(settlementKey))
			.thenReturn(false);
		when(persistence.existsByReferenceAndOperation(
			WALLET_ID,
			BID,
			REFERENCE_ID,
			SETTLEMENT
		)).thenReturn(false);
		when(persistence.findRemainingHold(WALLET_ID, BID, REFERENCE_ID))
			.thenReturn(9_999L);

		assertError(
			() -> service.settle(
				BID,
				REFERENCE_ID,
				MEMBER_ID,
				10_000L,
				settlementKey
			),
			INVALID_SETTLEMENT_AMOUNT
		);
		verify(persistence, never()).save(any());
	}

	private void stubNewOperation(
		TradePointOperationType operationType,
		long availableBalance,
		long heldBalance
	) {
		String key = switch (operationType) {
			case HOLD -> IDEMPOTENCY_KEY;
			case RELEASE -> "BID:351:RELEASE";
			case SETTLEMENT -> "BID:351:SETTLEMENT";
		};
		when(persistence.existsByIdempotencyKey(key)).thenReturn(false);
		when(persistence.lockTradeWallet(MEMBER_ID))
			.thenReturn(Optional.of(wallet(
				availableBalance,
				heldBalance
			)));
		when(persistence.existsByReferenceAndOperation(
			WALLET_ID,
			BID,
			REFERENCE_ID,
			operationType
		)).thenReturn(false);
	}

	private LockedTradeWallet wallet(
		long availableBalance,
		long heldBalance
	) {
		return new LockedTradeWallet(
			WALLET_ID,
			MEMBER_ID,
			availableBalance,
			heldBalance
		);
	}

	private void assertSavedOperation(
		TradePointOperationType operationType,
		long amount,
		long availableBalanceAfter,
		long heldBalanceAfter,
		String idempotencyKey
	) {
		ArgumentCaptor<NewOperation> captor =
			ArgumentCaptor.forClass(NewOperation.class);
		verify(persistence).save(captor.capture());
		assertThat(captor.getValue()).isEqualTo(new NewOperation(
			WALLET_ID,
			BID,
			REFERENCE_ID,
			operationType,
			amount,
			availableBalanceAfter,
			heldBalanceAfter,
			idempotencyKey
		));
	}

	private void assertError(
		ThrowingOperation operation,
		TradePointOperationErrorCode errorCode
	) {
		assertThatThrownBy(operation::run)
			.isInstanceOfSatisfying(
				TradePointOperationException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(errorCode)
			);
	}

	@FunctionalInterface
	private interface ThrowingOperation {
		void run();
	}
}
