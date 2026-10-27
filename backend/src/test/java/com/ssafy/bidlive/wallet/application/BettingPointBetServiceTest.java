package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.BettingPointBetErrorCode.BETTING_WALLET_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.BettingPointBetErrorCode.IDEMPOTENCY_KEY_REUSED;
import static com.ssafy.bidlive.wallet.application.BettingPointBetErrorCode.INSUFFICIENT_BETTING_POINTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.ssafy.bidlive.wallet.application.BettingPointBetPersistence.LedgerRecord;
import com.ssafy.bidlive.wallet.application.BettingPointBetPersistence.LockedWallet;
import com.ssafy.bidlive.wallet.application.BettingPointBetPersistence.NewBet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 예측 스테이크가 베팅 지갑에서 원자적이고 멱등하게 차감되는 흐름을 검증한다. */
@ExtendWith(MockitoExtension.class)
class BettingPointBetServiceTest {

	private static final String IDEMPOTENCY_KEY = "PREDICTION_BET:request-701";

	@Mock
	private BettingPointBetPersistence persistence;

	private BettingPointBetService service;

	@BeforeEach
	void setUp() {
		service = new BettingPointBetService(persistence);
	}

	@Test
	void deductsStakeFromLockedBettingWallet() {
		BettingPointBetCommand command = command(300L);
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(Optional.empty());
		when(persistence.lockBettingWallet(25L))
			.thenReturn(Optional.of(new LockedWallet(10L, 25L, 2_000L, 50L)));
		when(persistence.save(any(NewBet.class)))
			.thenAnswer(invocation -> saved(invocation.getArgument(0), 1001L));

		BettingPointBetResult result = service.deduct(command);

		assertThat(result).isEqualTo(new BettingPointBetResult(
			10L,
			1001L,
			701L,
			300L,
			1_700L,
			false
		));
		ArgumentCaptor<NewBet> captor =
			ArgumentCaptor.forClass(NewBet.class);
		verify(persistence).save(captor.capture());
		assertThat(captor.getValue()).isEqualTo(new NewBet(
			10L,
			25L,
			701L,
			300L,
			1_700L,
			50L,
			IDEMPOTENCY_KEY
		));
		verify(persistence, times(2))
			.findByIdempotencyKey(IDEMPOTENCY_KEY);
	}

	@Test
	void returnsExistingResultForSameIdempotentRequest() {
		BettingPointBetCommand command = command(300L);
		LedgerRecord existing = existing(true, -300L);
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(Optional.of(existing));

		BettingPointBetResult result = service.deduct(command);

		assertThat(result.duplicated()).isTrue();
		assertThat(result.ledgerId()).isEqualTo(1001L);
		assertThat(result.availableBalanceAfter()).isEqualTo(1_700L);
		verify(persistence, never()).lockBettingWallet(any());
		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsIdempotencyKeyReusedWithDifferentAmount() {
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(Optional.of(existing(true, -200L)));

		assertBetError(
			() -> service.deduct(command(300L)),
			IDEMPOTENCY_KEY_REUSED
		);
		verify(persistence, never()).lockBettingWallet(any());
		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsIdempotencyKeyUsedByAnotherTransactionType() {
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(Optional.of(existing(false, 300L)));

		assertBetError(
			() -> service.deduct(command(300L)),
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

		assertBetError(
			() -> service.deduct(command(300L)),
			BETTING_WALLET_NOT_FOUND
		);
		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsInsufficientBalanceWithoutWritingLedger() {
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(Optional.empty());
		when(persistence.lockBettingWallet(25L))
			.thenReturn(Optional.of(new LockedWallet(10L, 25L, 299L, 0L)));

		assertBetError(
			() -> service.deduct(command(300L)),
			INSUFFICIENT_BETTING_POINTS
		);
		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsInvalidCommandValues() {
		assertThatThrownBy(() -> new BettingPointBetCommand(
			25L,
			701L,
			0L,
			IDEMPOTENCY_KEY
		)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new BettingPointBetCommand(
			25L,
			701L,
			300L,
			" "
		)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> service.deduct(null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private BettingPointBetCommand command(long amount) {
		return new BettingPointBetCommand(
			25L,
			701L,
			amount,
			IDEMPOTENCY_KEY
		);
	}

	private LedgerRecord existing(
		boolean betTransaction,
		long availableDelta
	) {
		return new LedgerRecord(
			1001L,
			10L,
			25L,
			701L,
			betTransaction,
			availableDelta,
			1_700L,
			0L,
			IDEMPOTENCY_KEY
		);
	}

	private LedgerRecord saved(NewBet bet, Long ledgerId) {
		return new LedgerRecord(
			ledgerId,
			bet.walletId(),
			bet.memberId(),
			bet.predictionId(),
			true,
			-bet.amount(),
			bet.availableBalanceAfter(),
			bet.heldBalanceAfter(),
			bet.idempotencyKey()
		);
	}

	private void assertBetError(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
		BettingPointBetErrorCode expected
	) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(
				BettingPointBetException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(expected)
			);
	}
}
