package com.ssafy.bidlive.deal.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.deal.application.DealEscrowPersistence;
import com.ssafy.bidlive.deal.application.DealEscrowTarget;
import com.ssafy.bidlive.deal.application.ShippingAddressCommand;
import com.ssafy.bidlive.deal.domain.DealStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/**
 * 거래 전이를 네이티브 쿼리와 행 잠금으로 처리한다.
 *
 * <p>`deal_shipping` 갱신은 모두 UPSERT다. 낙찰 정산이 그 행을 미리 만들어 두는지가 아직 정해지지
 * 않았는데(정산 구현은 별개 작업), UPSERT면 있든 없든 같은 결과가 된다 — `UPDATE`로 두면 행이 없는
 * 경우에 조용히 0행이 갱신되고 상태만 앞으로 나가 배송지 없는 발송 대기가 만들어진다.
 */
@Repository
public class JpaDealEscrowAdapter implements DealEscrowPersistence {

	/**
	 * 에스크로를 푼 상태 값이다. `deal.escrow_status`에는 제약도 명세 표도 없어 이 도메인에서 정했다.
	 * 값을 바꾸려면 여기 한 곳만 고치면 된다 — 읽는 쪽이 아직 없다.
	 */
	private static final String ESCROW_RELEASED = "RELEASED";

	/**
	 * 판매자 지급 원장의 거래 유형이다. PNT-02 「포인트 원장 내역 조회」가 유형을 닫힌 집합
	 * (`CHARGE`·`HOLD`·`RELEASE`·`SETTLEMENT`·`REFUND`·`GRANT`·`BET`·`PAYOUT`)으로 못박았고,
	 * 그중 "거래 정산"에 해당하는 값이 `SETTLEMENT`다. 원인 도메인(`referenceType=DEAL`)은 이 표에
	 * 별도 컬럼이 없어 `deal_id`를 채우는 것으로 표현한다.
	 */
	private static final String PAYOUT_TRANSACTION_TYPE = "SETTLEMENT";

	private static final String LOCK_DEAL = """
		SELECT
			deal_id,
			buyer_member_id,
			seller_member_id,
			deal_status,
			escrow_amount
		FROM deal
		WHERE deal_id = :dealId
		FOR UPDATE
		""";

	private static final String UPSERT_SHIPPING_ADDRESS = """
		INSERT INTO deal_shipping (
			deal_id,
			recipient_name,
			recipient_phone,
			postal_code,
			address,
			address_detail,
			address_submitted_at
		)
		VALUES (
			:dealId,
			:recipientName,
			:recipientPhone,
			:postalCode,
			:address,
			NULLIF(:addressDetail, ''),
			:submittedAt
		)
		ON CONFLICT (deal_id) DO UPDATE SET
			recipient_name = EXCLUDED.recipient_name,
			recipient_phone = EXCLUDED.recipient_phone,
			postal_code = EXCLUDED.postal_code,
			address = EXCLUDED.address,
			address_detail = EXCLUDED.address_detail,
			address_submitted_at = EXCLUDED.address_submitted_at
		""";

	private static final String UPSERT_SHIPMENT = """
		INSERT INTO deal_shipping (deal_id, shipped_at, auto_confirm_at)
		VALUES (:dealId, :shippedAt, :autoConfirmAt)
		ON CONFLICT (deal_id) DO UPDATE SET
			shipped_at = EXCLUDED.shipped_at,
			auto_confirm_at = EXCLUDED.auto_confirm_at
		""";

	private static final String UPSERT_RECEIPT = """
		INSERT INTO deal_shipping (deal_id, received_confirmed_at)
		VALUES (:dealId, :confirmedAt)
		ON CONFLICT (deal_id) DO UPDATE SET
			received_confirmed_at = EXCLUDED.received_confirmed_at
		""";

	private static final String UPDATE_STATUS = """
		UPDATE deal
		SET deal_status = :status
		WHERE deal_id = :dealId
		""";

	private static final String COMPLETE_DEAL = """
		UPDATE deal
		SET deal_status = :status,
			escrow_status = :escrowStatus,
			completed_at = :completedAt
		WHERE deal_id = :dealId
		""";

	/**
	 * 자동 확정 대상. `deal_shipping`을 INNER JOIN하는 이유는 발송 확인이 있어야
	 * `auto_confirm_at`이 채워지고, 그 값이 없는 거래는 애초에 대상이 아니기 때문이다.
	 */
	private static final String FIND_AUTO_CONFIRM_DUE = """
		SELECT d.deal_id
		FROM deal d
		JOIN deal_shipping ds ON ds.deal_id = d.deal_id
		WHERE d.deal_status = :waitingReceipt
			AND ds.auto_confirm_at IS NOT NULL
			AND ds.auto_confirm_at <= :dueAt
		ORDER BY ds.auto_confirm_at, d.deal_id
		""";

	private static final String EXISTS_LEDGER_KEY = """
		SELECT EXISTS (
			SELECT 1 FROM trade_point_ledger WHERE idempotency_key = :idempotencyKey
		)
		""";

	/**
	 * 판매자 TRADE 지갑에 가용 잔액을 더하고 갱신된 잔액을 돌려준다.
	 *
	 * <p>`ON CONFLICT`가 조회·잠금·갱신을 한 문장으로 합친다. 지갑은 가입 때 만들어지지만
	 * (`WalletInitializationService`) 없는 회원이 섞여 있어도 이 문장은 만들고 넣는다 — 지급이
	 * "지갑이 없어서" 실패하면 대금이 어디에도 남지 않는다.
	 */
	private static final String CREDIT_SELLER_WALLET = """
		INSERT INTO wallet (member_id, wallet_type, available_balance, held_balance)
		VALUES (:memberId, 'TRADE', :amount, 0)
		ON CONFLICT (member_id, wallet_type) DO UPDATE SET
			available_balance = wallet.available_balance + EXCLUDED.available_balance
		RETURNING wallet_id, available_balance, held_balance
		""";

	private static final String INSERT_PAYOUT_LEDGER = """
		INSERT INTO trade_point_ledger (
			wallet_id,
			transaction_type,
			available_delta,
			held_delta,
			available_balance_after,
			held_balance_after,
			deal_id,
			idempotency_key
		)
		VALUES (
			:walletId,
			:transactionType,
			:amount,
			0,
			:availableBalanceAfter,
			:heldBalanceAfter,
			:dealId,
			:idempotencyKey
		)
		""";

	private final EntityManager entityManager;

	public JpaDealEscrowAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<DealEscrowTarget> lockDeal(long dealId) {
		List<?> rows = entityManager.createNativeQuery(LOCK_DEAL)
			.setParameter("dealId", dealId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new DealEscrowTarget(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue(),
			((Number) row[2]).longValue(),
			(String) row[3],
			((Number) row[4]).longValue()
		));
	}

	@Override
	public void submitAddress(
		long dealId,
		ShippingAddressCommand address,
		Instant submittedAt
	) {
		entityManager.createNativeQuery(UPSERT_SHIPPING_ADDRESS)
			.setParameter("dealId", dealId)
			.setParameter("recipientName", address.receiverName())
			.setParameter("recipientPhone", address.contact())
			.setParameter("postalCode", address.postalCode())
			.setParameter("address", address.address())
			// 네이티브 쿼리에 null을 그대로 바인딩하면 타입을 못 정해 터진다 — 빈 문자열로 넘기고
			// SQL의 NULLIF가 NULL로 눕힌다
			.setParameter("addressDetail", nullToEmpty(address.detailAddress()))
			.setParameter("submittedAt", submittedAt)
			.executeUpdate();
		updateStatus(dealId, DealStatus.WAITING_SHIPMENT);
	}

	@Override
	public void confirmShipment(long dealId, Instant shippedAt, Instant autoConfirmAt) {
		entityManager.createNativeQuery(UPSERT_SHIPMENT)
			.setParameter("dealId", dealId)
			.setParameter("shippedAt", shippedAt)
			.setParameter("autoConfirmAt", autoConfirmAt)
			.executeUpdate();
		updateStatus(dealId, DealStatus.WAITING_RECEIPT);
	}

	@Override
	public void completeDeal(long dealId, Instant confirmedAt, DealStatus status) {
		entityManager.createNativeQuery(UPSERT_RECEIPT)
			.setParameter("dealId", dealId)
			.setParameter("confirmedAt", confirmedAt)
			.executeUpdate();
		entityManager.createNativeQuery(COMPLETE_DEAL)
			.setParameter("status", status.name())
			.setParameter("escrowStatus", ESCROW_RELEASED)
			.setParameter("completedAt", confirmedAt)
			.setParameter("dealId", dealId)
			.executeUpdate();
	}

	@Override
	public List<Long> findAutoConfirmDueDealIds(Instant dueAt, int limit) {
		List<?> rows = entityManager.createNativeQuery(FIND_AUTO_CONFIRM_DUE)
			.setParameter("waitingReceipt", DealStatus.WAITING_RECEIPT.name())
			.setParameter("dueAt", dueAt)
			.setMaxResults(limit)
			.getResultList();
		return rows.stream()
			.map(row -> ((Number) row).longValue())
			.toList();
	}

	@Override
	public boolean payoutRecorded(String idempotencyKey) {
		return (Boolean) entityManager.createNativeQuery(EXISTS_LEDGER_KEY)
			.setParameter("idempotencyKey", idempotencyKey)
			.getSingleResult();
	}

	@Override
	public void payoutToSeller(
		long sellerMemberId,
		long dealId,
		long amount,
		String idempotencyKey
	) {
		Object[] wallet = (Object[]) entityManager.createNativeQuery(CREDIT_SELLER_WALLET)
			.setParameter("memberId", sellerMemberId)
			.setParameter("amount", amount)
			.getSingleResult();

		entityManager.createNativeQuery(INSERT_PAYOUT_LEDGER)
			.setParameter("walletId", ((Number) wallet[0]).longValue())
			.setParameter("transactionType", PAYOUT_TRANSACTION_TYPE)
			.setParameter("amount", amount)
			.setParameter("availableBalanceAfter", ((Number) wallet[1]).longValue())
			.setParameter("heldBalanceAfter", ((Number) wallet[2]).longValue())
			.setParameter("dealId", dealId)
			.setParameter("idempotencyKey", idempotencyKey)
			.executeUpdate();
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private void updateStatus(long dealId, DealStatus status) {
		entityManager.createNativeQuery(UPDATE_STATUS)
			.setParameter("status", status.name())
			.setParameter("dealId", dealId)
			.executeUpdate();
	}
}
