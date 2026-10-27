package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.TradePointRefundErrorCode.DUPLICATE_REQUEST;
import static com.ssafy.bidlive.wallet.application.TradePointRefundErrorCode.INSUFFICIENT_AVAILABLE_AMOUNT;
import static com.ssafy.bidlive.wallet.application.TradePointRefundErrorCode.INVALID_REFUND_AMOUNT;
import static com.ssafy.bidlive.wallet.application.TradePointRefundErrorCode.TRADE_WALLET_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.TradePointRefundErrorCode.UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.ssafy.bidlive.wallet.application.TradePointRefundPersistence.LockedTradeWallet;
import com.ssafy.bidlive.wallet.application.TradePointRefundPersistence.NewRefund;
import com.ssafy.bidlive.wallet.application.TradePointRefundPersistence.RefundLedgerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** 환불 검증·원장 저장과 Redis 동기화 이벤트 발행을 검증한다. */
@ExtendWith(MockitoExtension.class)
class TradePointRefundServiceTest {

	private static final Instant NOW =
		Instant.parse("2026-07-20T12:40:00Z");
	private static final String IDEMPOTENCY_KEY =
		"00000000-0000-0000-0000-000000000497";

	@Mock
	private TradePointRefundPersistence persistence;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private TradePointRefundService service;

	@BeforeEach
	void setUp() {
		service = new TradePointRefundService(
			persistence,
			eventPublisher
		);
	}

	@Test
	void refundsOnlyAvailableBalanceAndCreatesLedger() {
		when(persistence.existsByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(false);
		when(persistence.lockTradeWallet(25L))
			.thenReturn(Optional.of(new LockedTradeWallet(
				10L,
				25L,
				150_000L,
				50_000L
			)));
		when(persistence.save(any())).thenReturn(
			new RefundLedgerRecord(
				1003L,
				120_000L,
				50_000L,
				NOW
			)
		);

		TradePointRefundResult result = service.refund(command(30_000L));

		assertThat(result.refundedAmount()).isEqualTo(30_000L);
		assertThat(result.availableAmount()).isEqualTo(120_000L);
		assertThat(result.heldAmount()).isEqualTo(50_000L);
		assertThat(result.balance()).isEqualTo(170_000L);
		assertThat(result.ledgerId()).isEqualTo(1003L);
		assertThat(result.refundedAt()).isEqualTo(NOW);

		ArgumentCaptor<NewRefund> captor =
			ArgumentCaptor.forClass(NewRefund.class);
		verify(persistence).save(captor.capture());
		assertThat(captor.getValue()).isEqualTo(new NewRefund(
			10L,
			30_000L,
			120_000L,
			50_000L,
			IDEMPOTENCY_KEY
		));
		verify(eventPublisher).publishEvent(
			new TradeWalletBalanceChangedEvent(
				25L,
				1003L,
				-30_000L,
				0L,
				120_000L,
				50_000L
			)
		);
	}

	@Test
	void rejectsInvalidMemberAmountAndIdempotencyKey() {
		assertError(
			new TradePointRefundCommand(
				null,
				30_000L,
				IDEMPOTENCY_KEY
			),
			UNAUTHORIZED
		);
		assertError(command(0L), INVALID_REFUND_AMOUNT);
		assertError(command(-1L), INVALID_REFUND_AMOUNT);
		assertError(
			new TradePointRefundCommand(25L, 30_000L, "not-uuid"),
			INVALID_REFUND_AMOUNT
		);
		assertError(
			new TradePointRefundCommand(
				25L,
				30_000L,
				" " + IDEMPOTENCY_KEY
			),
			INVALID_REFUND_AMOUNT
		);

		verifyNoInteractions(persistence);
	}

	@Test
	void rejectsDuplicateBeforeWalletMutation() {
		when(persistence.existsByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(true);

		assertError(command(30_000L), DUPLICATE_REQUEST);

		verify(persistence).lockIdempotencyKey(IDEMPOTENCY_KEY);
		verify(persistence, never()).lockTradeWallet(any());
		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsMissingWalletAndInsufficientAvailableAmount() {
		when(persistence.existsByIdempotencyKey(IDEMPOTENCY_KEY))
			.thenReturn(false);
		when(persistence.lockTradeWallet(25L))
			.thenReturn(Optional.empty());

		assertError(command(30_000L), TRADE_WALLET_NOT_FOUND);
		verify(persistence, never()).save(any());

		when(persistence.lockTradeWallet(25L))
			.thenReturn(Optional.of(new LockedTradeWallet(
				10L,
				25L,
				29_999L,
				100_000L
			)));

		assertError(command(30_000L), INSUFFICIENT_AVAILABLE_AMOUNT);
		verify(persistence, never()).save(any());
	}

	private TradePointRefundCommand command(Long amount) {
		return new TradePointRefundCommand(
			25L,
			amount,
			IDEMPOTENCY_KEY
		);
	}

	private void assertError(
		TradePointRefundCommand command,
		TradePointRefundErrorCode errorCode
	) {
		assertThatThrownBy(() -> service.refund(command))
			.isInstanceOfSatisfying(
				TradePointRefundException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(errorCode)
			);
	}
}
