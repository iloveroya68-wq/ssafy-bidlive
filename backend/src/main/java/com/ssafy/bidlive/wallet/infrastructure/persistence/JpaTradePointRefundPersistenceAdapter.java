package com.ssafy.bidlive.wallet.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.wallet.application.TradePointRefundPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** PostgreSQL 잠금으로 TRADE 지갑 환불과 REFUND 원장을 직렬화한다. */
@Repository
public class JpaTradePointRefundPersistenceAdapter
	implements TradePointRefundPersistence {

	private static final String LOCK_IDEMPOTENCY_KEY_QUERY = """
		SELECT pg_advisory_xact_lock(
			hashtextextended(CAST(:idempotencyKey AS TEXT), 0)
		)
		""";

	private static final String EXISTS_LEDGER_QUERY = """
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

	private static final String UPDATE_WALLET_QUERY = """
		UPDATE wallet
		SET available_balance = :availableBalanceAfter
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
			auction_participant_id,
			bid_id,
			deal_id,
			point_charge_id,
			idempotency_key
		)
		VALUES (
			:walletId,
			'REFUND',
			:availableDelta,
			0,
			:availableBalanceAfter,
			:heldBalanceAfter,
			NULL,
			NULL,
			NULL,
			NULL,
			:idempotencyKey
		)
		RETURNING trade_point_ledger_id, created_at
		""";

	private final EntityManager entityManager;

	public JpaTradePointRefundPersistenceAdapter(
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
			.createNativeQuery(EXISTS_LEDGER_QUERY)
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
	public RefundLedgerRecord save(NewRefund refund) {
		int updatedRows = entityManager
			.createNativeQuery(UPDATE_WALLET_QUERY)
			.setParameter(
				"availableBalanceAfter",
				refund.availableBalanceAfter()
			)
			.setParameter("walletId", refund.walletId())
			.executeUpdate();
		if (updatedRows != 1) {
			throw new IllegalStateException(
				"Expected exactly one trade wallet row to be updated"
			);
		}

		Query insert = entityManager
			.createNativeQuery(INSERT_LEDGER_QUERY)
			.setParameter("walletId", refund.walletId())
			.setParameter("availableDelta", -refund.amount())
			.setParameter(
				"availableBalanceAfter",
				refund.availableBalanceAfter()
			)
			.setParameter(
				"heldBalanceAfter",
				refund.heldBalanceAfter()
			)
			.setParameter(
				"idempotencyKey",
				refund.idempotencyKey()
			);
		Object[] row = (Object[]) insert.getSingleResult();
		return new RefundLedgerRecord(
			asLong(row[0]),
			refund.availableBalanceAfter(),
			refund.heldBalanceAfter(),
			toInstant(row[1])
		);
	}

	private long asLong(Object value) {
		return ((Number) value).longValue();
	}

	private Instant toInstant(Object value) {
		return switch (value) {
			case Instant instant -> instant;
			case Timestamp timestamp -> timestamp.toInstant();
			case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			default -> throw new IllegalStateException(
				"Unsupported time type: " + value.getClass()
			);
		};
	}
}
