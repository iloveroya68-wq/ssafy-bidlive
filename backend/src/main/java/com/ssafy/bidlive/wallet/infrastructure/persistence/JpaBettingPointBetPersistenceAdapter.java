package com.ssafy.bidlive.wallet.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.wallet.application.BettingPointBetPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** PostgreSQL 행 잠금과 네이티브 쿼리로 BET 차감 원장을 기록한다. */
@Repository
public class JpaBettingPointBetPersistenceAdapter
	implements BettingPointBetPersistence {

	private static final String FIND_LEDGER_QUERY = """
		SELECT
			ledger.betting_point_ledger_id,
			ledger.wallet_id,
			wallet.member_id,
			ledger.prediction_id,
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
			'BET',
			:availableDelta,
			0,
			:availableBalanceAfter,
			:heldBalanceAfter,
			:predictionId,
			NULL,
			:idempotencyKey
		)
		RETURNING betting_point_ledger_id
		""";

	private final EntityManager entityManager;

	public JpaBettingPointBetPersistenceAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<LedgerRecord> findByIdempotencyKey(
		String idempotencyKey
	) {
		List<?> rows = entityManager.createNativeQuery(FIND_LEDGER_QUERY)
			.setParameter("idempotencyKey", idempotencyKey)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(toLedgerRecord((Object[]) rows.getFirst()));
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
	public LedgerRecord save(NewBet bet) {
		int updatedRows = entityManager.createNativeQuery(UPDATE_WALLET_QUERY)
			.setParameter(
				"availableBalanceAfter",
				bet.availableBalanceAfter()
			)
			.setParameter("walletId", bet.walletId())
			.executeUpdate();
		if (updatedRows != 1) {
			throw new IllegalStateException(
				"Expected exactly one betting wallet row to be updated"
			);
		}

		long availableDelta = -bet.amount();
		Query insert = entityManager.createNativeQuery(INSERT_LEDGER_QUERY)
			.setParameter("walletId", bet.walletId())
			.setParameter("availableDelta", availableDelta)
			.setParameter(
				"availableBalanceAfter",
				bet.availableBalanceAfter()
			)
			.setParameter("heldBalanceAfter", bet.heldBalanceAfter())
			.setParameter("predictionId", bet.predictionId())
			.setParameter("idempotencyKey", bet.idempotencyKey());
		Long ledgerId = asLong(insert.getSingleResult());

		return new LedgerRecord(
			ledgerId,
			bet.walletId(),
			bet.memberId(),
			bet.predictionId(),
			true,
			availableDelta,
			bet.availableBalanceAfter(),
			bet.heldBalanceAfter(),
			bet.idempotencyKey()
		);
	}

	private LedgerRecord toLedgerRecord(Object[] row) {
		return new LedgerRecord(
			asLong(row[0]),
			asLong(row[1]),
			asLong(row[2]),
			asNullableLong(row[3]),
			"BET".equals(row[4]),
			asLong(row[5]),
			asLong(row[6]),
			asLong(row[7]),
			(String) row[8]
		);
	}

	private Long asNullableLong(Object value) {
		return value == null ? null : asLong(value);
	}

	private Long asLong(Object value) {
		return ((Number) value).longValue();
	}
}
