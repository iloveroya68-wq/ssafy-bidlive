package com.ssafy.bidlive.wallet.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.ssafy.bidlive.wallet.application.PointLedgerQueryCriteria;
import com.ssafy.bidlive.wallet.application.PointLedgerQueryPage;
import com.ssafy.bidlive.wallet.application.PointLedgerQueryPersistence;
import com.ssafy.bidlive.wallet.application.PointLedgerQueryRow;
import com.ssafy.bidlive.wallet.domain.PointLedgerCursor;
import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import com.ssafy.bidlive.wallet.domain.PointLedgerTransactionType;
import com.ssafy.bidlive.wallet.domain.WalletType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** 거래·베팅 원장을 하나의 최신순 조회 결과로 결합한다. */
@Repository
public class JpaPointLedgerQueryPersistenceAdapter
	implements PointLedgerQueryPersistence {

	private static final String SELECT_WALLET_TYPES = """
		SELECT wallet_type
		FROM wallet
		WHERE member_id = :memberId
		""";

	private static final String SELECT_LEDGERS = """
		WITH point_ledgers AS (
			SELECT
				trade.trade_point_ledger_id AS ledger_id,
				'TRADE' AS wallet_type,
				trade.transaction_type,
				trade.available_delta,
				trade.held_delta,
				trade.available_balance_after,
				trade.held_balance_after,
				COALESCE(trade.reference_type, CASE
					WHEN trade.deal_id IS NOT NULL THEN 'DEAL'
					WHEN trade.point_charge_id IS NOT NULL
						OR trade.transaction_type = 'CHARGE'
						OR (
							trade.transaction_type = 'REFUND'
							AND trade.deal_id IS NULL
							AND participant.auction_room_id IS NULL
						)
						THEN 'PAYMENT'
					WHEN trade.transaction_type IN ('HOLD', 'RELEASE')
						THEN 'BID'
					WHEN participant.auction_room_id IS NOT NULL
						AND trade.transaction_type IN (
							'SETTLEMENT',
							'REFUND'
						)
						THEN 'AUCTION'
					ELSE 'ADMIN'
				END) AS reference_type,
				COALESCE(trade.reference_id, CASE
					WHEN trade.deal_id IS NOT NULL
						THEN trade.deal_id
					WHEN trade.point_charge_id IS NOT NULL
						THEN trade.point_charge_id
					WHEN trade.transaction_type IN ('HOLD', 'RELEASE')
						THEN COALESCE(
							trade.bid_id,
							trade.auction_participant_id
						)
					WHEN participant.auction_room_id IS NOT NULL
						AND trade.transaction_type IN (
							'SETTLEMENT',
							'REFUND'
						)
						THEN participant.auction_room_id
					ELSE NULL
				END) AS reference_id,
				trade.created_at,
				0 AS source_order
			FROM trade_point_ledger trade
			JOIN wallet trade_wallet
			  ON trade_wallet.wallet_id = trade.wallet_id
			LEFT JOIN bid trade_bid
			  ON trade_bid.bid_id = trade.bid_id
			LEFT JOIN auction_participant participant
			  ON participant.auction_participant_id = COALESCE(
				trade.auction_participant_id,
				trade_bid.auction_participant_id
			  )
			WHERE trade_wallet.member_id = :memberId

			UNION ALL

			SELECT
				betting.betting_point_ledger_id AS ledger_id,
				'BETTING' AS wallet_type,
				betting.transaction_type,
				betting.available_delta,
				betting.held_delta,
				betting.available_balance_after,
				betting.held_balance_after,
				CASE
					WHEN betting.prediction_id IS NOT NULL
						OR betting.prediction_result_id IS NOT NULL
						OR betting.transaction_type IN (
							'BET',
							'PAYOUT',
							'REFUND'
						)
						THEN 'PREDICTION'
					WHEN betting.transaction_type = 'GRANT'
						THEN 'REWARD'
					ELSE 'ADMIN'
				END AS reference_type,
				COALESCE(
					betting.prediction_id,
					betting.prediction_result_id
				) AS reference_id,
				betting.created_at,
				1 AS source_order
			FROM betting_point_ledger betting
			JOIN wallet betting_wallet
			  ON betting_wallet.wallet_id = betting.wallet_id
			WHERE betting_wallet.member_id = :memberId
		)
		SELECT
			ledger_id,
			wallet_type,
			transaction_type,
			available_delta,
			held_delta,
			available_balance_after,
			held_balance_after,
			reference_type,
			reference_id,
			created_at,
			source_order
		FROM point_ledgers
		WHERE 1 = 1
		""";

	private static final String ORDER_BY = """
		ORDER BY created_at DESC, ledger_id DESC, source_order ASC
		""";

	private final EntityManager entityManager;

	public JpaPointLedgerQueryPersistenceAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public Set<WalletType> findWalletTypes(long memberId) {
		EnumSet<WalletType> walletTypes = EnumSet.noneOf(WalletType.class);
		List<?> rows = entityManager.createNativeQuery(SELECT_WALLET_TYPES)
			.setParameter("memberId", memberId)
			.getResultList();
		for (Object row : rows) {
			walletTypes.add(WalletType.valueOf((String) row));
		}
		return walletTypes;
	}

	@Override
	public PointLedgerQueryPage find(PointLedgerQueryCriteria criteria) {
		StringBuilder sql = new StringBuilder(SELECT_LEDGERS);
		appendFilters(sql, criteria);
		Query query = entityManager.createNativeQuery(sql + ORDER_BY);
		bindParameters(query, criteria);
		query.setMaxResults(criteria.size() + 1);

		List<?> resultRows = query.getResultList();
		List<PointLedgerQueryRow> rows = new ArrayList<>(
			resultRows
				.stream()
				.map(JpaPointLedgerQueryPersistenceAdapter::toRow)
				.toList()
		);
		boolean hasNext = rows.size() > criteria.size();
		if (hasNext) {
			rows = rows.subList(0, criteria.size());
		}
		String nextCursor = hasNext
			? cursorOf(rows.get(rows.size() - 1)).encode()
			: null;
		return new PointLedgerQueryPage(rows, nextCursor, hasNext);
	}

	private void appendFilters(
		StringBuilder sql,
		PointLedgerQueryCriteria criteria
	) {
		if (criteria.walletType() != null) {
			sql.append("\nAND wallet_type = :walletType\n");
		}
		if (criteria.transactionType() != null) {
			sql.append("\nAND transaction_type = :transactionType\n");
		}
		if (criteria.cursor() != null) {
			sql.append("""

				AND (
					created_at < CAST(:cursorCreatedAt AS TIMESTAMPTZ)
					OR (
						created_at =
							CAST(:cursorCreatedAt AS TIMESTAMPTZ)
						AND ledger_id < :cursorLedgerId
					)
					OR (
						created_at =
							CAST(:cursorCreatedAt AS TIMESTAMPTZ)
						AND ledger_id = :cursorLedgerId
						AND source_order > :cursorSourceOrder
					)
				)
				""");
		}
	}

	private void bindParameters(
		Query query,
		PointLedgerQueryCriteria criteria
	) {
		query.setParameter("memberId", criteria.memberId());
		if (criteria.walletType() != null) {
			query.setParameter(
				"walletType",
				criteria.walletType().name()
			);
		}
		if (criteria.transactionType() != null) {
			query.setParameter(
				"transactionType",
				criteria.transactionType().name()
			);
		}
		if (criteria.cursor() != null) {
			query.setParameter(
				"cursorCreatedAt",
				criteria.cursor().createdAt().toString()
			);
			query.setParameter(
				"cursorLedgerId",
				criteria.cursor().ledgerId()
			);
			query.setParameter(
				"cursorSourceOrder",
				criteria.cursor().sourceOrder()
			);
		}
	}

	private static PointLedgerQueryRow toRow(Object value) {
		Object[] row = (Object[]) value;
		return new PointLedgerQueryRow(
			toLong(row[0]),
			WalletType.valueOf((String) row[1]),
			PointLedgerTransactionType.valueOf((String) row[2]),
			toLong(row[3]),
			toLong(row[4]),
			toLong(row[5]),
			toLong(row[6]),
			PointLedgerReferenceType.valueOf((String) row[7]),
			toNullableLong(row[8]),
			toInstant(row[9])
		);
	}

	private static PointLedgerCursor cursorOf(PointLedgerQueryRow row) {
		return new PointLedgerCursor(
			row.createdAt(),
			row.ledgerId(),
			row.walletType()
		);
	}

	private static long toLong(Object value) {
		return ((Number) value).longValue();
	}

	private static Long toNullableLong(Object value) {
		return value == null ? null : toLong(value);
	}

	private static Instant toInstant(Object value) {
		return switch (value) {
			case Instant instant -> instant;
			case Timestamp timestamp -> timestamp.toInstant();
			case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			default -> throw new IllegalStateException(
				"지원하지 않는 시각 타입: " + value.getClass()
			);
		};
	}
}
