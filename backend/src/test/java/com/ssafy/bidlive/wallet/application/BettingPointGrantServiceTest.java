package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.BettingPointGrantErrorCode.BETTING_WALLET_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.BettingPointGrantErrorCode.INVALID_GRANT_AMOUNT;
import static com.ssafy.bidlive.wallet.application.BettingPointGrantErrorCode.INVALID_GRANT_TYPE;
import static com.ssafy.bidlive.wallet.application.BettingPointGrantErrorCode.MEMBER_INACTIVE;
import static com.ssafy.bidlive.wallet.application.BettingPointGrantErrorCode.MEMBER_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.BettingPointGrantErrorCode.POINT_LEDGER_WRITE_FAILED;
import static com.ssafy.bidlive.wallet.domain.BettingPointGrantType.LIVE_WATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.ssafy.bidlive.member.domain.MemberStatus;
import com.ssafy.bidlive.wallet.application.BettingPointGrantPersistence.GrantRecord;
import com.ssafy.bidlive.wallet.application.BettingPointGrantPersistence.LockedBettingWallet;
import com.ssafy.bidlive.wallet.application.BettingPointGrantPersistence.MemberRecord;
import com.ssafy.bidlive.wallet.application.BettingPointGrantPersistence.NewGrant;
import com.ssafy.bidlive.wallet.domain.BettingPointGrantPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** 일반 보상의 원장 저장과 Redis 동기화 이벤트 발행을 검증한다. */
@ExtendWith(MockitoExtension.class)
class BettingPointGrantServiceTest {

	private static final Long MEMBER_ID = 25L;
	private static final Long WALLET_ID = 10L;
	private static final Long LEDGER_ID = 1001L;
	private static final String SOURCE_ID = "AUCTION:100";
	private static final Instant OCCURRED_AT =
		Instant.parse("2026-07-22T01:30:00Z");
	private static final Instant GRANTED_AT =
		Instant.parse("2026-07-22T01:30:01Z");
	private static final UUID GRANT_ID = UUID.fromString(
		"019d2f6f-2ce5-7f73-8cb5-00db2f54d69d"
	);

	@Mock
	private BettingPointGrantPersistence persistence;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private BettingPointGrantService service;

	@BeforeEach
	void setUp() {
		service = new BettingPointGrantService(
			persistence,
			eventPublisher
		);
	}

	@Test
	void grantsPolicyAmountAndCreatesLedger() {
		when(persistence.findByBusinessKey(
			MEMBER_ID,
			LIVE_WATCH,
			SOURCE_ID
		)).thenReturn(Optional.empty());
		when(persistence.lockMember(MEMBER_ID)).thenReturn(Optional.of(
			new MemberRecord(MEMBER_ID, MemberStatus.ACTIVE)
		));
		when(persistence.lockBettingWallet(MEMBER_ID)).thenReturn(
			Optional.of(new LockedBettingWallet(
				WALLET_ID,
				MEMBER_ID,
				2_000L,
				500L
			))
		);
		when(persistence.save(any())).thenReturn(record(2_600L));

		BettingPointGrantResult result = service.grant(command(100L));

		assertThat(result).isEqualTo(new BettingPointGrantResult(
			GRANT_ID,
			MEMBER_ID,
			LIVE_WATCH,
			100L,
			2_600L,
			LEDGER_ID,
			false,
			GRANTED_AT
		));

		ArgumentCaptor<NewGrant> captor =
			ArgumentCaptor.forClass(NewGrant.class);
		verify(persistence).save(captor.capture());
		NewGrant saved = captor.getValue();
		assertThat(saved.walletId()).isEqualTo(WALLET_ID);
		assertThat(saved.memberId()).isEqualTo(MEMBER_ID);
		assertThat(saved.grantType()).isEqualTo(LIVE_WATCH);
		assertThat(saved.sourceId()).isEqualTo(SOURCE_ID);
		assertThat(saved.amount()).isEqualTo(100L);
		assertThat(saved.policyVersion())
			.isEqualTo(BettingPointGrantPolicy.VERSION);
		assertThat(saved.occurredAt()).isEqualTo(OCCURRED_AT);
		assertThat(saved.availableBalanceAfter()).isEqualTo(2_100L);
		assertThat(saved.heldBalanceAfter()).isEqualTo(500L);
		assertThat(saved.grantId()).isNotNull();
		verify(eventPublisher).publishEvent(
			new BettingWalletBalanceChangedEvent(MEMBER_ID, 100L)
		);
	}

	@Test
	void returnsExistingResultForDuplicateRequest() {
		GrantRecord existing = record(2_100L);
		when(persistence.findByBusinessKey(
			MEMBER_ID,
			LIVE_WATCH,
			SOURCE_ID
		)).thenReturn(Optional.of(existing));

		BettingPointGrantResult result = service.grant(command(999L));

		assertThat(result.duplicated()).isTrue();
		assertThat(result.grantId()).isEqualTo(GRANT_ID);
		assertThat(result.ledgerId()).isEqualTo(LEDGER_ID);
		assertThat(result.balanceAfter()).isEqualTo(2_100L);
		verify(persistence, never()).lockMember(any());
		verify(persistence, never()).lockBettingWallet(any());
		verify(persistence, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void rejectsUnsupportedTypeSourceAndPolicyAmount() {
		assertError(
			new BettingPointGrantCommand(
				MEMBER_ID,
				null,
				SOURCE_ID,
				100L,
				BettingPointGrantPolicy.VERSION,
				OCCURRED_AT
			),
			INVALID_GRANT_TYPE
		);
		assertError(
			new BettingPointGrantCommand(
				MEMBER_ID,
				LIVE_WATCH,
				" ",
				100L,
				BettingPointGrantPolicy.VERSION,
				OCCURRED_AT
			),
			INVALID_GRANT_TYPE
		);
		assertError(command(0L), INVALID_GRANT_AMOUNT);
		assertError(command(101L), INVALID_GRANT_AMOUNT);
		assertError(
			new BettingPointGrantCommand(
				MEMBER_ID,
				LIVE_WATCH,
				SOURCE_ID,
				100L,
				"old-policy",
				OCCURRED_AT
			),
			INVALID_GRANT_AMOUNT
		);

		verify(persistence, never()).save(any());
	}

	@Test
	void rejectsMissingOrInactiveMemberAndMissingWallet() {
		stubNoDuplicate();
		when(persistence.lockMember(MEMBER_ID))
			.thenReturn(Optional.empty());
		assertError(command(100L), MEMBER_NOT_FOUND);

		when(persistence.lockMember(MEMBER_ID)).thenReturn(Optional.of(
			new MemberRecord(MEMBER_ID, MemberStatus.SUSPENDED)
		));
		assertError(command(100L), MEMBER_INACTIVE);

		when(persistence.lockMember(MEMBER_ID)).thenReturn(Optional.of(
			new MemberRecord(MEMBER_ID, MemberStatus.ACTIVE)
		));
		when(persistence.lockBettingWallet(MEMBER_ID))
			.thenReturn(Optional.empty());
		assertError(command(100L), BETTING_WALLET_NOT_FOUND);

		verify(persistence, never()).save(any());
	}

	@Test
	void mapsUnexpectedPersistenceFailureToLedgerWriteFailure() {
		when(persistence.findByBusinessKey(
			MEMBER_ID,
			LIVE_WATCH,
			SOURCE_ID
		)).thenThrow(new IllegalStateException("database unavailable"));

		assertError(command(100L), POINT_LEDGER_WRITE_FAILED);
	}

	private void stubNoDuplicate() {
		when(persistence.findByBusinessKey(
			MEMBER_ID,
			LIVE_WATCH,
			SOURCE_ID
		)).thenReturn(Optional.empty());
	}

	private BettingPointGrantCommand command(long amount) {
		return new BettingPointGrantCommand(
			MEMBER_ID,
			LIVE_WATCH,
			SOURCE_ID,
			amount,
			BettingPointGrantPolicy.VERSION,
			OCCURRED_AT
		);
	}

	private GrantRecord record(long balanceAfter) {
		return new GrantRecord(
			GRANT_ID,
			MEMBER_ID,
			LIVE_WATCH,
			SOURCE_ID,
			100L,
			BettingPointGrantPolicy.VERSION,
			balanceAfter,
			LEDGER_ID,
			GRANTED_AT
		);
	}

	private void assertError(
		BettingPointGrantCommand command,
		BettingPointGrantErrorCode errorCode
	) {
		assertThatThrownBy(() -> service.grant(command))
			.isInstanceOfSatisfying(
				BettingPointGrantException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(errorCode)
			);
	}
}
