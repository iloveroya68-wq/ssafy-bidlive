package com.ssafy.bidlive.deal.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.deal.application.DealEscrowTarget;
import com.ssafy.bidlive.deal.application.ShippingAddressCommand;
import com.ssafy.bidlive.deal.domain.DealStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 전이별 저장 결과와 판매자 지급(지갑·원장)을 실제 PostgreSQL로 검증한다. */
@SpringBootTest
@Transactional
class JpaDealEscrowAdapterIntegrationTest {

	private static final Instant SUBMITTED_AT = Instant.parse("2026-07-21T12:00:00Z");
	private static final Instant SHIPPED_AT = Instant.parse("2026-07-22T01:00:00Z");
	private static final Instant AUTO_CONFIRM_AT = Instant.parse("2026-07-29T01:00:00Z");
	private static final Instant CONFIRMED_AT = Instant.parse("2026-07-23T09:00:00Z");

	@Autowired
	private JpaDealEscrowAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		DealTestFixture.insert(entityManager);
	}

	@Test
	void readsPartiesStatusAndEscrowAmountInOneQuery() {
		DealEscrowTarget target =
			adapter.lockDeal(DealTestFixture.WAITING_ADDRESS_DEAL_ID).orElseThrow();

		assertThat(target.buyerMemberId()).isEqualTo(DealTestFixture.BUYER_ID);
		assertThat(target.sellerMemberId()).isEqualTo(DealTestFixture.SELLER_ID);
		assertThat(target.status()).isEqualTo("WAITING_ADDRESS");
		assertThat(target.escrowAmount()).isEqualTo(DealTestFixture.FINAL_PRICE);
	}

	@Test
	void returnsEmptyForMissingDeal() {
		assertThat(adapter.lockDeal(999_999L)).isEmpty();
	}

	/** 배송지 입력 시점에는 `deal_shipping` 행이 없다 — UPSERT가 만들어야 한다. */
	@Test
	void insertsShippingRowAndMovesStatusForward() {
		adapter.submitAddress(
			DealTestFixture.WAITING_ADDRESS_DEAL_ID,
			address("김서연", null),
			SUBMITTED_AT
		);

		assertThat(shipping(DealTestFixture.WAITING_ADDRESS_DEAL_ID)).satisfies(row -> {
			assertThat(row[0]).isEqualTo("김서연");
			assertThat(row[1]).isEqualTo("010-1234-5678");
			assertThat(row[2]).isEqualTo("06236");
			assertThat(row[3]).isEqualTo("서울시 강남구 테헤란로 1");
			// 빈 상세주소는 NULLIF가 NULL로 눕힌다
			assertThat(row[4]).isNull();
			assertThat(DealTimestamps.toInstant(row[5])).isEqualTo(SUBMITTED_AT);
		});
		assertThat(status(DealTestFixture.WAITING_ADDRESS_DEAL_ID)).isEqualTo("WAITING_SHIPMENT");
	}

	/** 이미 배송지가 있으면 덮어쓴다 — 같은 단계에서 다시 입력하는 경우다. */
	@Test
	void overwritesExistingShippingRow() {
		adapter.submitAddress(
			DealTestFixture.WAITING_SHIPMENT_DEAL_ID,
			address("박지훈", "202동 303호"),
			SUBMITTED_AT
		);

		assertThat(shipping(DealTestFixture.WAITING_SHIPMENT_DEAL_ID)).satisfies(row -> {
			assertThat(row[0]).isEqualTo("박지훈");
			assertThat(row[4]).isEqualTo("202동 303호");
		});
	}

	@Test
	void recordsShipmentAndAutoConfirmTime() {
		adapter.submitAddress(
			DealTestFixture.WAITING_ADDRESS_DEAL_ID,
			address("김서연", "101동 202호"),
			SUBMITTED_AT
		);

		adapter.confirmShipment(
			DealTestFixture.WAITING_ADDRESS_DEAL_ID,
			SHIPPED_AT,
			AUTO_CONFIRM_AT
		);

		Object[] row = shipping(DealTestFixture.WAITING_ADDRESS_DEAL_ID);
		assertThat(DealTimestamps.toInstant(row[6])).isEqualTo(SHIPPED_AT);
		assertThat(DealTimestamps.toInstant(row[7])).isEqualTo(AUTO_CONFIRM_AT);
		assertThat(status(DealTestFixture.WAITING_ADDRESS_DEAL_ID)).isEqualTo("WAITING_RECEIPT");
	}

	@Test
	void marksDealCompletedAndReleasesEscrow() {
		adapter.completeDeal(
			DealTestFixture.WAITING_RECEIPT_DEAL_ID,
			CONFIRMED_AT,
			DealStatus.COMPLETED
		);

		Object[] deal = (Object[]) entityManager.createNativeQuery("""
			SELECT deal_status, escrow_status, completed_at
			FROM deal
			WHERE deal_id = :dealId
			""")
			.setParameter("dealId", DealTestFixture.WAITING_RECEIPT_DEAL_ID)
			.getSingleResult();

		assertThat(deal[0]).isEqualTo("COMPLETED");
		assertThat(deal[1]).isEqualTo("RELEASED");
		assertThat(DealTimestamps.toInstant(deal[2])).isEqualTo(CONFIRMED_AT);
		assertThat(DealTimestamps.toInstant(
			shipping(DealTestFixture.WAITING_RECEIPT_DEAL_ID)[8]
		)).isEqualTo(CONFIRMED_AT);
	}

	/** 지갑이 이미 있으면 가용 잔액에 더하고, 원장에는 더한 뒤의 잔액이 남는다. */
	@Test
	void creditsExistingSellerWalletAndWritesLedgerRow() {
		DealTestFixture.execute(entityManager, """
			INSERT INTO wallet (member_id, wallet_type, available_balance, held_balance)
			VALUES (9401, 'TRADE', 1000, 500)
			""");
		entityManager.flush();

		adapter.payoutToSeller(
			DealTestFixture.SELLER_ID,
			DealTestFixture.WAITING_RECEIPT_DEAL_ID,
			DealTestFixture.FINAL_PRICE,
			"DEAL_COMPLETION_PAYOUT:9403"
		);

		Object[] wallet = tradeWallet(DealTestFixture.SELLER_ID);
		assertThat(((Number) wallet[1]).longValue()).isEqualTo(1000 + DealTestFixture.FINAL_PRICE);
		assertThat(((Number) wallet[2]).longValue()).isEqualTo(500L);

		Object[] ledger = (Object[]) entityManager.createNativeQuery("""
			SELECT
				ledger.transaction_type,
				ledger.available_delta,
				ledger.held_delta,
				ledger.available_balance_after,
				ledger.held_balance_after,
				ledger.deal_id,
				ledger.wallet_id
			FROM trade_point_ledger ledger
			WHERE ledger.idempotency_key = 'DEAL_COMPLETION_PAYOUT:9403'
			""").getSingleResult();

		assertThat(ledger[0]).isEqualTo("SETTLEMENT");
		assertThat(((Number) ledger[1]).longValue()).isEqualTo(DealTestFixture.FINAL_PRICE);
		assertThat(((Number) ledger[2]).longValue()).isZero();
		assertThat(((Number) ledger[3]).longValue()).isEqualTo(1000 + DealTestFixture.FINAL_PRICE);
		assertThat(((Number) ledger[4]).longValue()).isEqualTo(500L);
		assertThat(((Number) ledger[5]).longValue())
			.isEqualTo(DealTestFixture.WAITING_RECEIPT_DEAL_ID);
		assertThat(((Number) ledger[6]).longValue()).isEqualTo(((Number) wallet[0]).longValue());
	}

	/**
	 * 지갑이 없는 판매자에게도 지급된다. 가입 때 지갑을 만들지만(`WalletInitializationService`)
	 * 없는 회원이 섞여 있어도 대금이 사라지면 안 된다.
	 */
	@Test
	void createsWalletWhenSellerHasNone() {
		adapter.payoutToSeller(
			DealTestFixture.STRANGER_ID,
			DealTestFixture.WAITING_RECEIPT_DEAL_ID,
			DealTestFixture.FINAL_PRICE,
			"DEAL_COMPLETION_PAYOUT:9403"
		);

		Object[] wallet = tradeWallet(DealTestFixture.STRANGER_ID);
		assertThat(((Number) wallet[1]).longValue()).isEqualTo(DealTestFixture.FINAL_PRICE);
		assertThat(((Number) wallet[2]).longValue()).isZero();
	}

	@Test
	void reportsWhetherPayoutKeyIsAlreadyRecorded() {
		String key = "DEAL_COMPLETION_PAYOUT:9403";
		assertThat(adapter.payoutRecorded(key)).isFalse();

		adapter.payoutToSeller(
			DealTestFixture.SELLER_ID,
			DealTestFixture.WAITING_RECEIPT_DEAL_ID,
			DealTestFixture.FINAL_PRICE,
			key
		);

		assertThat(adapter.payoutRecorded(key)).isTrue();
	}

	/**
	 * 자동 확정 대상은 「수령 대기 + 기한 지남」 둘을 모두 만족하는 건뿐이다.
	 *
	 * <p>픽스처의 수령 대기 거래(9403)는 `auto_confirm_at`이 2026-07-27이고, 배송지 대기(9401)·발송
	 * 대기(9402)는 상태부터 아니다. 기한 이전 시각으로 물으면 아무것도 나오지 않아야 한다.
	 */
	@Test
	void findsOnlyWaitingReceiptDealsPastTheirDeadline() {
		assertThat(adapter.findAutoConfirmDueDealIds(Instant.parse("2026-07-28T00:00:00Z"), 10))
			.containsExactly(DealTestFixture.WAITING_RECEIPT_DEAL_ID);

		// 기한 1초 전이면 아직 대상이 아니다
		assertThat(adapter.findAutoConfirmDueDealIds(Instant.parse("2026-07-27T00:59:59Z"), 10))
			.isEmpty();
	}

	/** 발송 확인 전에는 `auto_confirm_at`이 없다 — 상태가 수령 대기가 되어도 기한이 없으면 제외다. */
	@Test
	void excludesDealWithoutDeadline() {
		DealTestFixture.execute(entityManager, """
			UPDATE deal SET deal_status = 'WAITING_RECEIPT' WHERE deal_id = 9402
			""");
		DealTestFixture.execute(entityManager, """
			UPDATE deal_shipping SET auto_confirm_at = NULL WHERE deal_id = 9402
			""");
		entityManager.flush();

		assertThat(adapter.findAutoConfirmDueDealIds(Instant.parse("2026-07-28T00:00:00Z"), 10))
			.doesNotContain(9402L);
	}

	/** 한 번에 훑는 상한이 걸린다 — 남은 건은 다음 주기가 가져간다. */
	@Test
	void limitsBatchSize() {
		DealTestFixture.execute(entityManager, """
			UPDATE deal SET deal_status = 'WAITING_RECEIPT' WHERE deal_id = 9402
			""");
		DealTestFixture.execute(entityManager, """
			UPDATE deal_shipping SET auto_confirm_at = '2026-07-26T00:00:00Z' WHERE deal_id = 9402
			""");
		entityManager.flush();

		// 오래된 기한이 먼저다 — 9402(07-26)가 9403(07-27)보다 앞선다
		assertThat(adapter.findAutoConfirmDueDealIds(Instant.parse("2026-07-28T00:00:00Z"), 1))
			.containsExactly(9402L);
	}

	/** 자동 확정은 같은 완료 경로를 쓰고 상태만 `AUTO_COMPLETED`로 남긴다. */
	@Test
	void marksAutoCompletedWithSameCompletionPath() {
		adapter.completeDeal(
			DealTestFixture.WAITING_RECEIPT_DEAL_ID,
			CONFIRMED_AT,
			DealStatus.AUTO_COMPLETED
		);

		Object[] deal = (Object[]) entityManager.createNativeQuery("""
			SELECT deal_status, escrow_status, completed_at
			FROM deal
			WHERE deal_id = :dealId
			""")
			.setParameter("dealId", DealTestFixture.WAITING_RECEIPT_DEAL_ID)
			.getSingleResult();

		assertThat(deal[0]).isEqualTo("AUTO_COMPLETED");
		assertThat(deal[1]).isEqualTo("RELEASED");
		assertThat(DealTimestamps.toInstant(deal[2])).isEqualTo(CONFIRMED_AT);
		// 확정된 뒤에는 대상에서 빠진다
		assertThat(adapter.findAutoConfirmDueDealIds(Instant.parse("2026-07-28T00:00:00Z"), 10))
			.isEmpty();
	}

	private static ShippingAddressCommand address(String receiverName, String detailAddress) {
		return new ShippingAddressCommand(
			receiverName,
			"010-1234-5678",
			"06236",
			"서울시 강남구 테헤란로 1",
			detailAddress
		);
	}

	private Object[] shipping(long dealId) {
		return (Object[]) entityManager.createNativeQuery("""
			SELECT
				recipient_name,
				recipient_phone,
				postal_code,
				address,
				address_detail,
				address_submitted_at,
				shipped_at,
				auto_confirm_at,
				received_confirmed_at
			FROM deal_shipping
			WHERE deal_id = :dealId
			""")
			.setParameter("dealId", dealId)
			.getSingleResult();
	}

	private String status(long dealId) {
		return (String) entityManager.createNativeQuery("""
			SELECT deal_status FROM deal WHERE deal_id = :dealId
			""")
			.setParameter("dealId", dealId)
			.getSingleResult();
	}

	private Object[] tradeWallet(long memberId) {
		List<?> rows = entityManager.createNativeQuery("""
			SELECT wallet_id, available_balance, held_balance
			FROM wallet
			WHERE member_id = :memberId AND wallet_type = 'TRADE'
			""")
			.setParameter("memberId", memberId)
			.getResultList();
		assertThat(rows).hasSize(1);
		return (Object[]) rows.getFirst();
	}
}
