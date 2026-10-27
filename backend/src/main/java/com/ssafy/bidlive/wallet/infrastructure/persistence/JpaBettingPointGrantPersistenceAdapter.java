package com.ssafy.bidlive.wallet.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ssafy.bidlive.member.domain.MemberStatus;
import com.ssafy.bidlive.wallet.application.BettingPointGrantPersistence;
import com.ssafy.bidlive.wallet.domain.BettingPointGrantType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** PostgreSQL 잠금으로 일반 베팅 포인트 지급을 원자적으로 기록한다. */
@Repository
public class JpaBettingPointGrantPersistenceAdapter
	implements BettingPointGrantPersistence {

	private static final String LOCK_GRANT_KEY_QUERY = """
		SELECT pg_advisory_xact_lock(
			hashtextextended(CAST(:grantKey AS TEXT), 0)
		)
		""";

	private static final String FIND_GRANT_QUERY = """
		SELECT
			grant_history.grant_id,
			grant_history.member_id,
			grant_history.grant_type,
			grant_history.source_id,
			grant_history.amount,
			grant_history.policy_version,
			(
				ledger.available_balance_after
					+ ledger.held_balance_after
			) AS balance_after,
			grant_history.ledger_id,
			grant_history.granted_at
		FROM betting_point_grant grant_history
		JOIN betting_point_ledger ledger
			ON ledger.betting_point_ledger_id =
				grant_history.ledger_id
		WHERE grant_history.member_id = :memberId
			AND grant_history.grant_type = :grantType
			AND grant_history.source_id = :sourceId
		""";

	private static final String LOCK_MEMBER_QUERY = """
		SELECT member_id, status
		FROM member
		WHERE member_id = :memberId
		FOR SHARE
		""";

	private static final String LOCK_BETTING_WALLET_QUERY = """
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
			'GRANT',
			:amount,
			0,
			:availableBalanceAfter,
			:heldBalanceAfter,
			NULL,
			NULL,
			:idempotencyKey
		)
		RETURNING betting_point_ledger_id
		""";

	private static final String INSERT_GRANT_QUERY = """
		INSERT INTO betting_point_grant (
			grant_id,
			member_id,
			wallet_id,
			grant_type,
			source_id,
			amount,
			policy_version,
			ledger_id,
			occurred_at
		)
		VALUES (
			:grantId,
			:memberId,
			:walletId,
			:grantType,
			:sourceId,
			:amount,
			:policyVersion,
			:ledgerId,
			:occurredAt
		)
		RETURNING granted_at
		""";

	private final EntityManager entityManager;

	public JpaBettingPointGrantPersistenceAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public void lockGrantKey(
		Long memberId,
		BettingPointGrantType grantType,
		String sourceId
	) {
		entityManager.createNativeQuery(LOCK_GRANT_KEY_QUERY)
			.setParameter(
				"grantKey",
				grantKey(memberId, grantType, sourceId)
			)
			.getSingleResult();
	}

	@Override
	public Optional<GrantRecord> findByBusinessKey(
		Long memberId,
		BettingPointGrantType grantType,
		String sourceId
	) {
		List<?> rows = entityManager.createNativeQuery(FIND_GRANT_QUERY)
			.setParameter("memberId", memberId)
			.setParameter("grantType", grantType.name())
			.setParameter("sourceId", sourceId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(toGrantRecord((Object[]) rows.getFirst()));
	}

	@Override
	public Optional<MemberRecord> lockMember(Long memberId) {
		List<?> rows = entityManager.createNativeQuery(LOCK_MEMBER_QUERY)
			.setParameter("memberId", memberId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new MemberRecord(
			asLong(row[0]),
			MemberStatus.valueOf((String) row[1])
		));
	}

	@Override
	public Optional<LockedBettingWallet> lockBettingWallet(
		Long memberId
	) {
		List<?> rows = entityManager
			.createNativeQuery(LOCK_BETTING_WALLET_QUERY)
			.setParameter("memberId", memberId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new LockedBettingWallet(
			asLong(row[0]),
			asLong(row[1]),
			asLong(row[2]),
			asLong(row[3])
		));
	}

	@Override
	public GrantRecord save(NewGrant grant) {
		updateWallet(grant);
		Long ledgerId = insertLedger(grant);
		Instant grantedAt = insertGrant(grant, ledgerId);
		long balanceAfter = Math.addExact(
			grant.availableBalanceAfter(),
			grant.heldBalanceAfter()
		);
		return new GrantRecord(
			grant.grantId(),
			grant.memberId(),
			grant.grantType(),
			grant.sourceId(),
			grant.amount(),
			grant.policyVersion(),
			balanceAfter,
			ledgerId,
			grantedAt
		);
	}

	private void updateWallet(NewGrant grant) {
		int updatedRows = entityManager
			.createNativeQuery(UPDATE_WALLET_QUERY)
			.setParameter(
				"availableBalanceAfter",
				grant.availableBalanceAfter()
			)
			.setParameter("walletId", grant.walletId())
			.executeUpdate();
		if (updatedRows != 1) {
			throw new IllegalStateException(
				"Expected exactly one betting wallet row to be updated"
			);
		}
	}

	private Long insertLedger(NewGrant grant) {
		Query query = entityManager
			.createNativeQuery(INSERT_LEDGER_QUERY)
			.setParameter("walletId", grant.walletId())
			.setParameter("amount", grant.amount())
			.setParameter(
				"availableBalanceAfter",
				grant.availableBalanceAfter()
			)
			.setParameter(
				"heldBalanceAfter",
				grant.heldBalanceAfter()
			)
			.setParameter(
				"idempotencyKey",
				"GRANT:" + grant.grantId()
			);
		return asLong(query.getSingleResult());
	}

	private Instant insertGrant(NewGrant grant, Long ledgerId) {
		Object value = entityManager.createNativeQuery(INSERT_GRANT_QUERY)
			.setParameter("grantId", grant.grantId())
			.setParameter("memberId", grant.memberId())
			.setParameter("walletId", grant.walletId())
			.setParameter("grantType", grant.grantType().name())
			.setParameter("sourceId", grant.sourceId())
			.setParameter("amount", grant.amount())
			.setParameter("policyVersion", grant.policyVersion())
			.setParameter("ledgerId", ledgerId)
			.setParameter("occurredAt", grant.occurredAt())
			.getSingleResult();
		return toInstant(value);
	}

	private GrantRecord toGrantRecord(Object[] row) {
		return new GrantRecord(
			asUuid(row[0]),
			asLong(row[1]),
			BettingPointGrantType.valueOf((String) row[2]),
			(String) row[3],
			asLong(row[4]),
			(String) row[5],
			asLong(row[6]),
			asLong(row[7]),
			toInstant(row[8])
		);
	}

	private String grantKey(
		Long memberId,
		BettingPointGrantType grantType,
		String sourceId
	) {
		return "BETTING_GRANT:"
			+ memberId
			+ ":"
			+ grantType.name()
			+ ":"
			+ sourceId;
	}

	private Long asLong(Object value) {
		return ((Number) value).longValue();
	}

	private UUID asUuid(Object value) {
		return value instanceof UUID uuid
			? uuid
			: UUID.fromString(value.toString());
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
