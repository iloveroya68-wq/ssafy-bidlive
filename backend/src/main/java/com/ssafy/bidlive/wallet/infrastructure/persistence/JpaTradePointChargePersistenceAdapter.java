package com.ssafy.bidlive.wallet.infrastructure.persistence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.wallet.application.TradePointChargePersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** PostgreSQL 잠금으로 TRADE 지갑 충전과 CHARGE 원장을 직렬화해 저장한다. */
@Repository
public class JpaTradePointChargePersistenceAdapter
	implements TradePointChargePersistence {

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

	private static final String SUM_DAILY_CHARGE_QUERY = """
		SELECT COALESCE(SUM(available_delta), 0)
		FROM trade_point_ledger
		WHERE wallet_id = :walletId
			AND transaction_type = 'CHARGE'
			AND created_at >= :startInclusive
			AND created_at < :endExclusive
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
			'CHARGE',
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

	public JpaTradePointChargePersistenceAdapter(
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
	public long sumChargedAmount(
		Long walletId,
		Instant startInclusive,
		Instant endExclusive
	) {
		Object value = entityManager
			.createNativeQuery(SUM_DAILY_CHARGE_QUERY)
			.setParameter("walletId", walletId)
			.setParameter("startInclusive", startInclusive)
			.setParameter("endExclusive", endExclusive)
			.getSingleResult();
		return asLongExact(value);
	}

	@Override
	public ChargeLedgerRecord save(NewCharge charge) {
		int updatedRows = entityManager
			.createNativeQuery(UPDATE_WALLET_QUERY)
			.setParameter(
				"availableBalanceAfter",
				charge.availableBalanceAfter()
			)
			.setParameter("walletId", charge.walletId())
			.executeUpdate();
		if (updatedRows != 1) {
			throw new IllegalStateException(
				"Expected exactly one trade wallet row to be updated"
			);
		}

		Query insert = entityManager
			.createNativeQuery(INSERT_LEDGER_QUERY)
			.setParameter("walletId", charge.walletId())
			.setParameter("availableDelta", charge.amount())
			.setParameter(
				"availableBalanceAfter",
				charge.availableBalanceAfter()
			)
			.setParameter(
				"heldBalanceAfter",
				charge.heldBalanceAfter()
			)
			.setParameter(
				"idempotencyKey",
				charge.idempotencyKey()
			);
		Object[] row = (Object[]) insert.getSingleResult();
		return new ChargeLedgerRecord(
			asLong(row[0]),
			charge.availableBalanceAfter(),
			charge.heldBalanceAfter(),
			toInstant(row[1])
		);
	}

	private long asLongExact(Object value) {
		return switch (value) {
			case BigDecimal decimal -> decimal.longValueExact();
			case BigInteger integer -> integer.longValueExact();
			case Number number -> number.longValue();
			default -> throw new IllegalStateException(
				"Unsupported numeric type: " + value.getClass()
			);
		};
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
