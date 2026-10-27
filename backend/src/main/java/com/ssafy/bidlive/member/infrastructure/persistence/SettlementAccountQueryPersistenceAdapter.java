package com.ssafy.bidlive.member.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.member.application.SettlementAccountQueryPersistence;
import com.ssafy.bidlive.member.application.SettlementAccountSnapshot;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 마스킹된 정산 계좌 필드만 PostgreSQL에서 조회한다. */
@Repository
public class SettlementAccountQueryPersistenceAdapter
	implements SettlementAccountQueryPersistence {

	private static final String SELECT_ACCOUNT = """
		SELECT
			bank_code,
			masked_account_number,
			account_holder_name,
			verified,
			updated_at
		FROM settlement_account
		WHERE member_id = :memberId
		""";

	private final EntityManager entityManager;

	public SettlementAccountQueryPersistenceAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<SettlementAccountSnapshot> findByMemberId(Long memberId) {
		List<?> rows = entityManager.createNativeQuery(SELECT_ACCOUNT)
			.setParameter("memberId", memberId)
			.setMaxResults(1)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(toSnapshot((Object[]) rows.getFirst()));
	}

	private SettlementAccountSnapshot toSnapshot(Object[] row) {
		return new SettlementAccountSnapshot(
			(String) row[0],
			(String) row[1],
			(String) row[2],
			(Boolean) row[3],
			toInstant(row[4])
		);
	}

	private Instant toInstant(Object value) {
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
