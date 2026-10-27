package com.ssafy.bidlive.deal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.deal.domain.DealStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 자동 확정이 수령 확인과 같은 지급 경로를 쓰는지, 상태가 바뀐 건을 건너뛰는지 검증한다. */
class DealAutoConfirmServiceTest {

	private static final long DEAL_ID = 300L;
	private static final long BUYER_ID = 11L;
	private static final long SELLER_ID = 22L;
	private static final long ESCROW_AMOUNT = 74_000L;
	private static final Instant NOW = Instant.parse("2026-07-29T01:00:00Z");
	private static final String PAYOUT_KEY = "DEAL_COMPLETION_PAYOUT:" + DEAL_ID;

	private DealEscrowPersistence persistence;
	private DealAutoConfirmService service;

	@BeforeEach
	void setUp() {
		persistence = mock(DealEscrowPersistence.class);
		service = new DealAutoConfirmService(persistence, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	/** 대상 조회 기준 시각은 "지금"이다 — 배치가 스스로 7일을 다시 계산하지 않는다. */
	@Test
	void asksForDealsDueAsOfNow() {
		when(persistence.findAutoConfirmDueDealIds(NOW, 50)).thenReturn(List.of(300L, 301L));

		assertThat(service.findDueDealIds(50)).containsExactly(300L, 301L);
		verify(persistence).findAutoConfirmDueDealIds(NOW, 50);
	}

	@Test
	void paysSellerAndMarksAutoCompleted() {
		givenDeal(DealStatus.WAITING_RECEIPT, ESCROW_AMOUNT);

		assertThat(service.confirmDueDeal(DEAL_ID)).isTrue();

		// 구매자 수령 확인과 **같은 멱등 키**여야 한다 — 두 경로가 서로의 중복을 막는 근거다
		verify(persistence).payoutToSeller(SELLER_ID, DEAL_ID, ESCROW_AMOUNT, PAYOUT_KEY);
		verify(persistence).completeDeal(DEAL_ID, NOW, DealStatus.AUTO_COMPLETED);
	}

	/** 조회와 처리 사이에 구매자가 직접 확인했으면 건너뛴다 — 이미 지급됐고 상태도 COMPLETED다. */
	@Test
	void skipsDealAlreadyConfirmedByBuyer() {
		givenDeal(DealStatus.COMPLETED, ESCROW_AMOUNT);

		assertThat(service.confirmDueDeal(DEAL_ID)).isFalse();

		verify(persistence, never()).payoutToSeller(anyLong(), anyLong(), anyLong(), anyString());
		verify(persistence, never()).completeDeal(anyLong(), any(), any());
	}

	@Test
	void skipsDealThatDisappeared() {
		when(persistence.lockDeal(DEAL_ID)).thenReturn(Optional.empty());

		assertThat(service.confirmDueDeal(DEAL_ID)).isFalse();
		verify(persistence, never()).payoutToSeller(anyLong(), anyLong(), anyLong(), anyString());
	}

	/** 배치가 두 번 집어도 지급은 한 번이다 — 원장에 키가 있으면 지급을 건너뛰고 완료만 시킨다. */
	@Test
	void skipsPayoutWhenLedgerKeyAlreadyRecorded() {
		givenDeal(DealStatus.WAITING_RECEIPT, ESCROW_AMOUNT);
		when(persistence.payoutRecorded(PAYOUT_KEY)).thenReturn(true);

		assertThat(service.confirmDueDeal(DEAL_ID)).isTrue();

		verify(persistence, never()).payoutToSeller(anyLong(), anyLong(), anyLong(), anyString());
		verify(persistence).completeDeal(DEAL_ID, NOW, DealStatus.AUTO_COMPLETED);
	}

	/** 원장은 증감 0인 행을 거부한다. 0원 거래는 지급을 건너뛰고 완료만 시킨다(수령 확인과 같다). */
	@Test
	void skipsPayoutForZeroEscrowButStillCompletes() {
		givenDeal(DealStatus.WAITING_RECEIPT, 0L);

		assertThat(service.confirmDueDeal(DEAL_ID)).isTrue();

		verify(persistence, never()).payoutToSeller(anyLong(), anyLong(), anyLong(), anyString());
		verify(persistence).completeDeal(DEAL_ID, NOW, DealStatus.AUTO_COMPLETED);
	}

	private void givenDeal(DealStatus status, long escrowAmount) {
		when(persistence.lockDeal(DEAL_ID)).thenReturn(Optional.of(new DealEscrowTarget(
			DEAL_ID,
			BUYER_ID,
			SELLER_ID,
			status.name(),
			escrowAmount
		)));
	}
}
