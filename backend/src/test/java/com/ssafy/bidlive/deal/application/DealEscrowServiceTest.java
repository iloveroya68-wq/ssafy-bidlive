package com.ssafy.bidlive.deal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ssafy.bidlive.deal.domain.DealErrorCode;
import com.ssafy.bidlive.deal.domain.DealStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 전이별 주체·상태 판정과 에스크로 지급 조건을 검증한다. */
class DealEscrowServiceTest {

	private static final long DEAL_ID = 300L;
	private static final long BUYER_ID = 11L;
	private static final long SELLER_ID = 22L;
	private static final long ESCROW_AMOUNT = 74_000L;
	private static final Instant NOW = Instant.parse("2026-07-22T01:00:00Z");
	private static final String PAYOUT_KEY = "DEAL_COMPLETION_PAYOUT:" + DEAL_ID;

	private DealEscrowPersistence persistence;
	private DealEscrowService service;

	@BeforeEach
	void setUp() {
		persistence = mock(DealEscrowPersistence.class);
		service = new DealEscrowService(persistence, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	// ── 배송지 입력 ──

	@Test
	void submitsAddressAndMovesToWaitingShipment() {
		givenDeal(DealStatus.WAITING_ADDRESS);

		DealTransitionResults.Address result =
			service.submitShippingAddress(DEAL_ID, BUYER_ID, address());

		assertThat(result.status()).isEqualTo(DealStatus.WAITING_SHIPMENT);
		assertThat(result.updatedAt()).isEqualTo(NOW);
		verify(persistence).submitAddress(DEAL_ID, address().normalized(), NOW);
	}

	@Test
	void rejectsSellerSubmittingAddress() {
		givenDeal(DealStatus.WAITING_ADDRESS);

		assertThatThrownBy(() -> service.submitShippingAddress(DEAL_ID, SELLER_ID, address()))
			.satisfies(e -> assertThat(codeOf(e)).isEqualTo(DealErrorCode.NOT_DEAL_BUYER));
	}

	@Test
	void rejectsAddressWhenDealAlreadyMovedOn() {
		givenDeal(DealStatus.WAITING_SHIPMENT);

		assertThatThrownBy(() -> service.submitShippingAddress(DEAL_ID, BUYER_ID, address()))
			.satisfies(e -> assertThat(codeOf(e)).isEqualTo(DealErrorCode.INVALID_DEAL_STATUS));
	}

	/** 잘못된 본문은 행을 잠그기 전에 걸러야 한다 — 남의 요청을 기다리게 할 이유가 없다. */
	@Test
	void rejectsBlankReceiverNameWithoutLockingTheDeal() {
		assertThatThrownBy(() -> service.submitShippingAddress(
			DEAL_ID,
			BUYER_ID,
			new ShippingAddressCommand("  ", "010", "06236", "서울시", "101동")
		)).satisfies(e -> assertThat(codeOf(e))
			.isEqualTo(DealErrorCode.INVALID_SHIPPING_ADDRESS));

		verify(persistence, never()).lockDeal(anyLong());
	}

	@Test
	void rejectsMissingBody() {
		assertThatThrownBy(() -> service.submitShippingAddress(DEAL_ID, BUYER_ID, null))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(DealErrorCode.INVALID_SHIPPING_ADDRESS));
	}

	// ── 발송 확인 ──

	@Test
	void confirmsShipmentAndSchedulesAutoConfirmSevenDaysLater() {
		givenDeal(DealStatus.WAITING_SHIPMENT);

		DealTransitionResults.Shipment result = service.confirmShipment(DEAL_ID, SELLER_ID);

		assertThat(result.status()).isEqualTo(DealStatus.WAITING_RECEIPT);
		assertThat(result.shippedAt()).isEqualTo(NOW);
		assertThat(result.autoConfirmAt()).isEqualTo(Instant.parse("2026-07-29T01:00:00Z"));
		verify(persistence).confirmShipment(DEAL_ID, NOW, result.autoConfirmAt());
	}

	@Test
	void rejectsBuyerConfirmingShipment() {
		givenDeal(DealStatus.WAITING_SHIPMENT);

		assertThatThrownBy(() -> service.confirmShipment(DEAL_ID, BUYER_ID))
			.satisfies(e -> assertThat(codeOf(e)).isEqualTo(DealErrorCode.NOT_DEAL_SELLER));
	}

	// ── 수령 확인 ──

	@Test
	void paysSellerAndCompletesDealOnReceipt() {
		givenDeal(DealStatus.WAITING_RECEIPT);

		DealTransitionResults.Receipt result = service.confirmReceipt(DEAL_ID, BUYER_ID);

		assertThat(result.status()).isEqualTo(DealStatus.COMPLETED);
		assertThat(result.sellerPayoutAmount()).isEqualTo(ESCROW_AMOUNT);
		assertThat(result.completedAt()).isEqualTo(NOW);
		verify(persistence).payoutToSeller(SELLER_ID, DEAL_ID, ESCROW_AMOUNT, PAYOUT_KEY);
		verify(persistence).completeDeal(DEAL_ID, NOW, DealStatus.COMPLETED);
	}

	/**
	 * 자동 확정 배치가 먼저 지급했으면 다시 지급하지 않는다. 상태 검사만으로도 API 경로는 막히지만
	 * 두 경로가 같은 거래를 처리할 수 있으므로 원장 키로 한 번 더 본다.
	 */
	@Test
	void skipsPayoutWhenLedgerKeyAlreadyRecorded() {
		givenDeal(DealStatus.WAITING_RECEIPT);
		when(persistence.payoutRecorded(PAYOUT_KEY)).thenReturn(true);

		service.confirmReceipt(DEAL_ID, BUYER_ID);

		verify(persistence, never()).payoutToSeller(anyLong(), anyLong(), anyLong(), anyString());
		verify(persistence).completeDeal(DEAL_ID, NOW, DealStatus.COMPLETED);
	}

	/** 원장은 증감 0인 행을 거부한다. 0원 거래는 지급을 건너뛰고 완료만 시킨다. */
	@Test
	void skipsPayoutForZeroEscrowButStillCompletes() {
		when(persistence.lockDeal(DEAL_ID)).thenReturn(Optional.of(new DealEscrowTarget(
			DEAL_ID,
			BUYER_ID,
			SELLER_ID,
			DealStatus.WAITING_RECEIPT.name(),
			0L
		)));

		DealTransitionResults.Receipt result = service.confirmReceipt(DEAL_ID, BUYER_ID);

		assertThat(result.sellerPayoutAmount()).isZero();
		verify(persistence, never()).payoutToSeller(anyLong(), anyLong(), anyLong(), anyString());
		verify(persistence).completeDeal(DEAL_ID, NOW, DealStatus.COMPLETED);
	}

	@Test
	void rejectsReceiptBeforeShipment() {
		givenDeal(DealStatus.WAITING_SHIPMENT);

		assertThatThrownBy(() -> service.confirmReceipt(DEAL_ID, BUYER_ID))
			.satisfies(e -> assertThat(codeOf(e)).isEqualTo(DealErrorCode.INVALID_DEAL_STATUS));
		verify(persistence, never()).payoutToSeller(anyLong(), anyLong(), anyLong(), anyString());
	}

	// ── 공통 ──

	@Test
	void rejectsMissingDeal() {
		when(persistence.lockDeal(DEAL_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.confirmReceipt(DEAL_ID, BUYER_ID))
			.satisfies(e -> assertThat(codeOf(e)).isEqualTo(DealErrorCode.DEAL_NOT_FOUND));
	}

	@Test
	void rejectsUnauthenticatedRequest() {
		assertThatThrownBy(() -> service.confirmShipment(DEAL_ID, null))
			.satisfies(e -> assertThat(codeOf(e)).isEqualTo(DealErrorCode.UNAUTHORIZED));
		verify(persistence, never()).lockDeal(anyLong());
	}

	/** 알 수 없는 상태 문자열도 "지금 단계가 아니다"로 막는다 — enum 변환 실패로 500을 내지 않는다. */
	@Test
	void rejectsUnknownStatusStringAsInvalidStatus() {
		when(persistence.lockDeal(DEAL_ID)).thenReturn(Optional.of(new DealEscrowTarget(
			DEAL_ID,
			BUYER_ID,
			SELLER_ID,
			"SOMETHING_ELSE",
			ESCROW_AMOUNT
		)));

		assertThatThrownBy(() -> service.confirmReceipt(DEAL_ID, BUYER_ID))
			.satisfies(e -> assertThat(codeOf(e)).isEqualTo(DealErrorCode.INVALID_DEAL_STATUS));
	}

	private void givenDeal(DealStatus status) {
		when(persistence.lockDeal(DEAL_ID)).thenReturn(Optional.of(new DealEscrowTarget(
			DEAL_ID,
			BUYER_ID,
			SELLER_ID,
			status.name(),
			ESCROW_AMOUNT
		)));
	}

	private static ShippingAddressCommand address() {
		return new ShippingAddressCommand(
			"김서연",
			"010-1234-5678",
			"06236",
			"서울시 강남구 테헤란로 1",
			"101동 202호"
		);
	}

	private static DealErrorCode codeOf(Throwable throwable) {
		assertThat(throwable).isInstanceOf(DealException.class);
		return ((DealException) throwable).getErrorCode();
	}
}
