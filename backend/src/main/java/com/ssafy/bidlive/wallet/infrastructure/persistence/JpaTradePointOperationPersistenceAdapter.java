package com.ssafy.bidlive.wallet.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.wallet.application.TradePointOperationPersistence;
import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import com.ssafy.bidlive.wallet.domain.TradePointOperationType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** PostgreSQL 행 잠금으로 TRADE 지갑과 홀드 원장을 직렬화한다. */
@Repository
public class JpaTradePointOperationPersistenceAdapter
	implements TradePointOperationPersistence {

	private static final String LOCK_IDEMPOTENCY_KEY_QUERY = """
		SELECT pg_advisory_xact_lock(
			hashtextextended(CAST(:idempotencyKey AS TEXT), 0)
		)
		""";

	private static final String EXISTS_IDEMPOTENCY_KEY_QUERY = """
		SELECT COUNT(*)
		FROM trade_point_ledger
		WHERE idempotency_key = :idempotencyKey
		""";

	private static final String LOCK_TRADE_WALLET_QUERY = """
		SELECT
			wallet_id,
			member_id,
			available_balance,
			held_balance
		FROM wallet
		WHERE member_id = :memberId
			AND wallet_type = 'TRADE'
		FOR UPDATE
		""";

	private static final String EXISTS_REFERENCE_OPERATION_QUERY = """
		SELECT COUNT(*)
		FROM trade_point_ledger
		WHERE wallet_id = :walletId
			AND reference_type = :referenceType
			AND reference_id = :referenceId
			AND transaction_type = :operationType
		""";

	private static final String SELECT_REMAINING_HOLD_QUERY = """
		SELECT COALESCE(SUM(held_delta), 0)
		FROM trade_point_ledger
		WHERE wallet_id = :walletId
			AND reference_type = :referenceType
			AND reference_id = :referenceId
			AND transaction_type IN (
				'HOLD',
				'RELEASE',
				'SETTLEMENT'
			)
		""";

	private static final String UPDATE_WALLET_QUERY = """
		UPDATE wallet
		SET available_balance = :availableBalanceAfter,
			held_balance = :heldBalanceAfter
		WHERE wallet_id = :walletId
		""";

	private static final String INSERT_LEDGER_QUERY = """
		INSERT INTO trade_point_ledger (
			wallet_id,
			transaction_type,
			available_delta,
			held_delta,
			available_balance_after,
			held_balance_after,
			reference_type,
			reference_id,
			idempotency_key
		)
		VALUES (
			:walletId,
			:operationType,
			:availableDelta,
			:heldDelta,
			:availableBalanceAfter,
			:heldBalanceAfter,
			:referenceType,
			:referenceId,
			:idempotencyKey
		)
		RETURNING trade_point_ledger_id
		""";

	private final EntityManager entityManager;

	public JpaTradePointOperationPersistenceAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public void lockIdempotencyKey(String idempotencyKey) {
		entityManager.createNativeQuery(LOCK_IDEMPOTENCY_KEY_QUERY)
			.setParameter("idempotencyKey", idempotencyKey)
			.getSingleResult();
	}

	@Override
	public boolean existsByIdempotencyKey(String idempotencyKey) {
		Number count = (Number) entityManager
			.createNativeQuery(EXISTS_IDEMPOTENCY_KEY_QUERY)
			.setParameter("idempotencyKey", idempotencyKey)
			.getSingleResult();
		return count.longValue() > 0;
	}

	@Override
	public Optional<LockedTradeWallet> lockTradeWallet(Long memberId) {
		List<?> rows = entityManager
			.createNativeQuery(LOCK_TRADE_WALLET_QUERY)
			.setParameter("memberId", memberId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new LockedTradeWallet(
			asLong(row[0]),
			asLong(row[1]),
			asLong(row[2]),
			asLong(row[3])
		));
	}

	@Override
	public boolean existsByReferenceAndOperation(
		Long walletId,
		PointLedgerReferenceType referenceType,
		Long referenceId,
		TradePointOperationType operationType
	) {
		Number count = (Number) referenceQuery(
			EXISTS_REFERENCE_OPERATION_QUERY,
			walletId,
			referenceType,
			referenceId
		)
			.setParameter("operationType", operationType.name())
			.getSingleResult();
		return count.longValue() > 0;
	}

	@Override
	public long findRemainingHold(
		Long walletId,
		PointLedgerReferenceType referenceType,
		Long referenceId
	) {
		Number amount = (Number) referenceQuery(
			SELECT_REMAINING_HOLD_QUERY,
			walletId,
			referenceType,
			referenceId
		).getSingleResult();
		return amount.longValue();
	}

	@Override
	public OperationLedgerRecord save(NewOperation operation) {
		int updatedRows = entityManager
			.createNativeQuery(UPDATE_WALLET_QUERY)
			.setParameter(
				"availableBalanceAfter",
				operation.availableBalanceAfter()
			)
			.setParameter(
				"heldBalanceAfter",
				operation.heldBalanceAfter()
			)
			.setParameter("walletId", operation.walletId())
			.executeUpdate();
		if (updatedRows != 1) {
			throw new IllegalStateException(
				"Expected exactly one trade wallet row to be updated"
			);
		}

		Query insert = entityManager
			.createNativeQuery(INSERT_LEDGER_QUERY)
			.setParameter("walletId", operation.walletId())
			.setParameter(
				"operationType",
				operation.operationType().name()
			)
			.setParameter(
				"availableDelta",
				operation.availableDelta()
			)
			.setParameter("heldDelta", operation.heldDelta())
			.setParameter(
				"availableBalanceAfter",
				operation.availableBalanceAfter()
			)
			.setParameter(
				"heldBalanceAfter",
				operation.heldBalanceAfter()
			)
			.setParameter(
				"referenceType",
				operation.referenceType().name()
			)
			.setParameter(
				"referenceId",
				operation.referenceId()
			)
			.setParameter(
				"idempotencyKey",
				operation.idempotencyKey()
			);
		Long ledgerId = asLong(insert.getSingleResult());
		return new OperationLedgerRecord(
			ledgerId,
			operation.availableBalanceAfter(),
			operation.heldBalanceAfter()
		);
	}

	private Query referenceQuery(
		String sql,
		Long walletId,
		PointLedgerReferenceType referenceType,
		Long referenceId
	) {
		return entityManager.createNativeQuery(sql)
			.setParameter("walletId", walletId)
			.setParameter("referenceType", referenceType.name())
			.setParameter("referenceId", referenceId);
	}

	private long asLong(Object value) {
		return ((Number) value).longValue();
	}
}
