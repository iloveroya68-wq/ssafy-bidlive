package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.BettingPointSettlementErrorCode.BETTING_POINT_BALANCE_OVERFLOW;
import static com.ssafy.bidlive.wallet.application.BettingPointSettlementErrorCode.BETTING_WALLET_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.BettingPointSettlementErrorCode.IDEMPOTENCY_KEY_REUSED;
import static com.ssafy.bidlive.wallet.domain.BettingPointSettlementType.PAYOUT;
import static com.ssafy.bidlive.wallet.domain.BettingPointSettlementType.REFUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.ssafy.bidlive.wallet.application.BettingPointSettlementPersistence.LockedWallet;
import com.ssafy.bidlive.wallet.application.BettingPointSettlementPersistence.NewSettlement;
import com.ssafy.bidlive.wallet.application.BettingPointSettlementPersistence.SettlementRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** 예측 배당·환불의 DB 반영과 Redis 동기화 이벤트 발행을 검증한다. */
@ExtendWith(MockitoExtension.class)
class BettingPointSettlementServiceTest {

	private static final String IDEMPOTENCY_KEY = "PREDICTION_SETTLEMENT:701";

	@Mock
	private BettingPointSettlementPersistence persistence;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private BettingPointSettlementService service;

	@BeforeEach
	void setUp() {
		service = new BettingPointSettlementService(
			persistence,
			eventPublisher
		);
	}

	@Test
	void settlesPayoutIntoLockedBettingWallet() {
		BettingPointSettlementCommand command = command(PAYOUT, 1_500L);
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(Optional.empty());
		when(persistence.lockBettingWallet(25L))
			.thenReturn(Optional.of(new LockedWallet(10L, 25L, 2_000L, 0L)));
		when(persistence.save(any(NewSettlement.class)))
			.thenAnswer(invocation -> saved(invocation.getArgument(0), 1001L));

		BettingPointSettlementResult result = service.settle(command);

		assertThat(result).isEqualTo(new BettingPointSettlementResult(
			10L,
			1001L,
			PAYOUT,
			1_500L,
			3_500L,
			false
		));
		ArgumentCaptor<NewSettlement> captor =
			ArgumentCaptor.forClass(NewSettlement.class);
		verify(persistence).save(captor.capture());
		assertThat(captor.getValue()).isEqualTo(new NewSettlement(
			10L,
			25L,
			701L,
			801L,
			PAYOUT,
			1_500L,
			3_500L,
			0L,
			IDEMPOTENCY_KEY
		));
		verify(persistence, times(2))
			.findByIdempotencyKey(IDEMPOTENCY_KEY);
		verify(eventPublisher).publishEvent(
			new BettingWalletBalanceChangedEvent(25L, 1_500L)
		);
	}

	@Test
	void returnsExistingResultForSameIdempotentRequest() {
		BettingPointSettlementCommand command = command(REFUND, 300L);
		SettlementRecord existing = new SettlementRecord(
			1001L,
			10L,
			25L,
			701L,
			801L,
			REFUND,
			300L,
			2_300L,
			0L,
			IDEMPOTENCY_KEY
		);
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(Optional.of(existing));

		BettingPointSettlementResult result = service.settle(command);

		assertThat(result.duplicated()).isTrue();
		assertThat(result.availableBalanceAfter()).isEqualTo(2_300L);
		verify(persistence, never()).lockBettingWallet(any());
		verify(persistence, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void rejectsIdempotencyKeyReusedWithDifferentAmount() {
		BettingPointSettlementCommand command = command(PAYOUT, 1_500L);
		SettlementRecord existing = new SettlementRecord(
			1001L,
			10L,
			25L,
			701L,
			801L,
			PAYOUT,
			900L,
			2_900L,
			0L,
			IDEMPOTENCY_KEY
		);
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(Optional.of(existing));

		assertSettlementError(
			() -> service.settle(command),
			IDEMPOTENCY_KEY_REUSED
		);
		verify(persistence, never()).lockBettingWallet(any());
		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsMemberWithoutBettingWallet() {
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(Optional.empty());
		when(persistence.lockBettingWallet(25L)).thenReturn(Optional.empty());

		assertSettlementError(
			() -> service.settle(command(PAYOUT, 1_500L)),
			BETTING_WALLET_NOT_FOUND
		);
		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsBalanceOverflowWithoutWritingLedger() {
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(Optional.empty());
		when(persistence.lockBettingWallet(25L))
			.thenReturn(Optional.of(new LockedWallet(
				10L,
				25L,
				Long.MAX_VALUE,
				0L
			)));

		assertSettlementError(
			() -> service.settle(command(PAYOUT, 1L)),
			BETTING_POINT_BALANCE_OVERFLOW
		);
		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsInvalidCommandValues() {
		assertThatThrownBy(() -> new BettingPointSettlementCommand(
			25L,
			701L,
			801L,
			PAYOUT,
			0L,
			IDEMPOTENCY_KEY
		)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new BettingPointSettlementCommand(
			25L,
			701L,
			801L,
			PAYOUT,
			100L,
			" "
		)).isInstanceOf(IllegalArgumentException.class);
	}

	private BettingPointSettlementCommand command(
		com.ssafy.bidlive.wallet.domain.BettingPointSettlementType type,
		long amount
	) {
		return new BettingPointSettlementCommand(
			25L,
			701L,
			801L,
			type,
			amount,
			IDEMPOTENCY_KEY
		);
	}

	private SettlementRecord saved(NewSettlement settlement, Long ledgerId) {
		return new SettlementRecord(
			ledgerId,
			settlement.walletId(),
			settlement.memberId(),
			settlement.predictionId(),
			settlement.predictionResultId(),
			settlement.settlementType(),
			settlement.amount(),
			settlement.availableBalanceAfter(),
			settlement.heldBalanceAfter(),
			settlement.idempotencyKey()
		);
	}

	private void assertSettlementError(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
		BettingPointSettlementErrorCode expected
	) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(
				BettingPointSettlementException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(expected)
			);
	}
}
