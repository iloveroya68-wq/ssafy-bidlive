package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.TradePointChargeErrorCode.CHARGE_LIMIT_EXCEEDED;
import static com.ssafy.bidlive.wallet.application.TradePointChargeErrorCode.DUPLICATE_REQUEST;
import static com.ssafy.bidlive.wallet.application.TradePointChargeErrorCode.INVALID_CHARGE_AMOUNT;
import static com.ssafy.bidlive.wallet.application.TradePointChargeErrorCode.TRADE_WALLET_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.TradePointChargeErrorCode.UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ssafy.bidlive.wallet.application.TradePointChargePersistence.ChargeLedgerRecord;
import com.ssafy.bidlive.wallet.application.TradePointChargePersistence.LockedTradeWallet;
import com.ssafy.bidlive.wallet.application.TradePointChargePersistence.NewCharge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** 충전 검증·원장 저장과 Redis 동기화 이벤트 발행을 검증한다. */
@ExtendWith(MockitoExtension.class)
class TradePointChargeServiceTest {

	private static final Instant NOW =
		Instant.parse("2026-07-20T12:35:00Z");
	private static final String IDEMPOTENCY_KEY =
		"00000000-0000-0000-0000-000000000496";

	@Mock
	private TradePointChargePersistence persistence;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private TradePointChargeService service;

	@BeforeEach
	void setUp() {
		service = new TradePointChargeService(
			persistence,
			eventPublisher,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void chargesAvailableBalanceAndCreatesLedger() {
		when(persistence.existsByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(false);
		when(persistence.lockTradeWallet(25L))
			.thenReturn(Optional.of(new LockedTradeWallet(
				10L,
				25L,
				50_000L,
				50_000L
			)));
		when(persistence.sumChargedAmount(any(), any(), any()))
			.thenReturn(100_000L);
		when(persistence.save(any())).thenReturn(
			new ChargeLedgerRecord(
				1002L,
				150_000L,
				50_000L,
				NOW
			)
		);

		TradePointChargeResult result = service.charge(command(100_000L));

		assertThat(result.chargedAmount()).isEqualTo(100_000L);
		assertThat(result.availableAmount()).isEqualTo(150_000L);
		assertThat(result.heldAmount()).isEqualTo(50_000L);
		assertThat(result.balance()).isEqualTo(200_000L);
		assertThat(result.ledgerId()).isEqualTo(1002L);
		assertThat(result.chargedAt()).isEqualTo(NOW);

		ArgumentCaptor<NewCharge> captor =
			ArgumentCaptor.forClass(NewCharge.class);
		verify(persistence).save(captor.capture());
		assertThat(captor.getValue()).isEqualTo(new NewCharge(
			10L,
			100_000L,
			150_000L,
			50_000L,
			IDEMPOTENCY_KEY
		));
		verify(eventPublisher).publishEvent(
			new TradeWalletBalanceChangedEvent(
				25L,
				1002L,
				100_000L,
				0L,
				150_000L,
				50_000L
			)
		);
	}

	@Test
	void rejectsInvalidMemberAndAmountBeforePersistenceAccess() {
		assertError(
			new TradePointChargeCommand(
				null,
				100_000L,
				IDEMPOTENCY_KEY
			),
			UNAUTHORIZED
		);
		assertError(command(0L), INVALID_CHARGE_AMOUNT);
		assertError(command(-1L), INVALID_CHARGE_AMOUNT);

		verifyNoInteractions(persistence);
	}

	@Test
	void rejectsSingleAndDailyLimitExceeded() {
		assertError(command(1_000_001L), CHARGE_LIMIT_EXCEEDED);
		verifyNoInteractions(persistence);

		when(persistence.existsByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(false);
		when(persistence.lockTradeWallet(25L))
			.thenReturn(Optional.of(new LockedTradeWallet(
				10L,
				25L,
				2_500_000L,
				0L
			)));
		when(persistence.sumChargedAmount(any(), any(), any()))
			.thenReturn(2_500_001L);

		assertError(command(500_000L), CHARGE_LIMIT_EXCEEDED);
		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsDuplicateIdempotencyKeyBeforeWalletMutation() {
		when(persistence.existsByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(true);

		assertError(command(100_000L), DUPLICATE_REQUEST);

		verify(persistence).lockIdempotencyKey(IDEMPOTENCY_KEY);
		verify(persistence, never()).lockTradeWallet(any());
		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsMissingTradeWallet() {
		when(persistence.existsByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(false);
		when(persistence.lockTradeWallet(25L))
			.thenReturn(Optional.empty());

		assertError(command(100_000L), TRADE_WALLET_NOT_FOUND);

		verify(persistence, never()).sumChargedAmount(any(), any(), any());
		verify(persistence, never()).save(any());
	}

	private TradePointChargeCommand command(Long amount) {
		return new TradePointChargeCommand(
			25L,
			amount,
			IDEMPOTENCY_KEY
		);
	}

	private void assertError(
		TradePointChargeCommand command,
		TradePointChargeErrorCode errorCode
	) {
		assertThatThrownBy(() -> service.charge(command))
			.isInstanceOfSatisfying(
				TradePointChargeException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(errorCode)
			);
	}
}
