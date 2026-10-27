package com.ssafy.bidlive.wallet.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.wallet.application.BettingPointSettlementPersistence;
import com.ssafy.bidlive.wallet.domain.BettingPointSettlementType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** PostgreSQL 행 잠금과 네이티브 쿼리로 베팅 지갑 정산 원장을 기록한다. */
@Repository
public class JpaBettingPointSettlementPersistenceAdapter
	implements BettingPointSettlementPersistence {

	private static final String FIND_SETTLEMENT_QUERY = """
		SELECT
			ledger.betting_point_ledger_id,
			ledger.wallet_id,
			wallet.member_id,
			ledger.prediction_id,
			ledger.prediction_result_id,
			ledger.transaction_type,
			ledger.available_delta,
			ledger.available_balance_after,
			ledger.held_balance_after,
			ledger.idempotency_key
		FROM betting_point_ledger ledger
		JOIN wallet
			ON wallet.wallet_id = ledger.wallet_id
		WHERE ledger.idempotency_key = :idempotencyKey
		""";

	private static final String LOCK_WALLET_QUERY = """
		SELECT
			wallet_id,
			member_id,
			available_balance,
			held_balance
		FROM wallet
		WHERE member_id = :memberId
			AND wallet_type = 'BETTING'
		FOR UPDATE
		""";

	private static final String UPDATE_WALLET_QUERY = """
		UPDATE wallet
		SET available_balance = :availableBalanceAfter
		WHERE wallet_id = :walletId
		""";

	private static final String INSERT_LEDGER_QUERY = """
		INSERT INTO betting_point_ledger (
			wallet_id,
			transaction_type,
			available_delta,
			held_delta,
			available_balance_after,
			held_balance_after,
			prediction_id,
			prediction_result_id,
			idempotency_key
		)
		VALUES (
			:walletId,
			:transactionType,
			:amount,
			0,
			:availableBalanceAfter,
			:heldBalanceAfter,
			:predictionId,
			:predictionResultId,
			:idempotencyKey
		)
		RETURNING betting_point_ledger_id
		""";

	private final EntityManager entityManager;

	public JpaBettingPointSettlementPersistenceAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<SettlementRecord> findByIdempotencyKey(
		String idempotencyKey
	) {
		List<?> rows = entityManager.createNativeQuery(FIND_SETTLEMENT_QUERY)
			.setParameter("idempotencyKey", idempotencyKey)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(toSettlementRecord((Object[]) rows.getFirst()));
	}

	@Override
	public Optional<LockedWallet> lockBettingWallet(Long memberId) {
		List<?> rows = entityManager.createNativeQuery(LOCK_WALLET_QUERY)
			.setParameter("memberId", memberId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}

		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new LockedWallet(
			asLong(row[0]),
			asLong(row[1]),
			asLong(row[2]),
			asLong(row[3])
		));
	}

	@Override
	public SettlementRecord save(NewSettlement settlement) {
		int updatedRows = entityManager.createNativeQuery(UPDATE_WALLET_QUERY)
			.setParameter(
				"availableBalanceAfter",
				settlement.availableBalanceAfter()
			)
			.setParameter("walletId", settlement.walletId())
			.executeUpdate();
		if (updatedRows != 1) {
			throw new IllegalStateException(
				"Expected exactly one betting wallet row to be updated"
			);
		}

		Query insert = entityManager.createNativeQuery(INSERT_LEDGER_QUERY)
			.setParameter("walletId", settlement.walletId())
			.setParameter(
				"transactionType",
				settlement.settlementType().name()
			)
			.setParameter("amount", settlement.amount())
			.setParameter(
				"availableBalanceAfter",
				settlement.availableBalanceAfter()
			)
			.setParameter("heldBalanceAfter", settlement.heldBalanceAfter())
			.setParameter("predictionId", settlement.predictionId())
			.setParameter(
				"predictionResultId",
				settlement.predictionResultId()
			)
			.setParameter("idempotencyKey", settlement.idempotencyKey());
		Long ledgerId = asLong(insert.getSingleResult());

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

	private SettlementRecord toSettlementRecord(Object[] row) {
		return new SettlementRecord(
			asLong(row[0]),
			asLong(row[1]),
			asLong(row[2]),
			asLong(row[3]),
			asLong(row[4]),
			BettingPointSettlementType.valueOf((String) row[5]),
			asLong(row[6]),
			asLong(row[7]),
			asLong(row[8]),
			(String) row[9]
		);
	}

	private Long asLong(Object value) {
		return ((Number) value).longValue();
	}
}
