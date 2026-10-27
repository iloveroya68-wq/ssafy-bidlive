package com.ssafy.bidlive.prediction.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.prediction.application.PredictionAchievementPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** JPA 네이티브 조회로 회원별 예측 통계와 등급·뱃지 읽기 모델을 구성한다. */
@Repository
public class JpaPredictionAchievementPersistenceAdapter
	implements PredictionAchievementPersistence {

	private static final String STATISTICS_QUERY = """
		SELECT
			COUNT(*) FILTER (WHERE pr.result_status IN ('HIT', 'MISS')),
			COUNT(*) FILTER (WHERE pr.result_status = 'HIT'),
			COALESCE(
				SUM(p.stake_amount)
					FILTER (WHERE pr.result_status IN ('HIT', 'MISS')),
				0
			),
			COALESCE(
				SUM(pr.payout_amount)
					FILTER (WHERE pr.result_status IN ('HIT', 'MISS')),
				0
			)
		FROM prediction_result pr
		JOIN prediction p
			ON p.prediction_id = pr.prediction_id
		JOIN auction_participant ap
			ON ap.auction_participant_id = p.auction_participant_id
		WHERE ap.member_id = :memberId
		""";

	private static final String GRADE_QUERY = """
		SELECT grade_code, grade_score
		FROM prediction_achievement
		WHERE member_id = :memberId
		""";

	private static final String BADGE_QUERY = """
		SELECT badge_code, earned_at
		FROM prediction_badge
		WHERE member_id = :memberId
		ORDER BY earned_at DESC, prediction_badge_id DESC
		""";

	private final EntityManager entityManager;

	public JpaPredictionAchievementPersistenceAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Statistics findStatistics(Long memberId) {
		Object[] row = (Object[]) query(STATISTICS_QUERY, memberId)
			.getSingleResult();
		return new Statistics(
			asLong(row[0]),
			asLong(row[1]),
			asLong(row[2]),
			asLong(row[3])
		);
	}

	@Override
	public Optional<Grade> findGrade(Long memberId) {
		List<?> rows = query(GRADE_QUERY, memberId).getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}

		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new Grade((String) row[0], asLong(row[1])));
	}

	@Override
	public List<Badge> findBadges(Long memberId) {
		List<?> rows = query(BADGE_QUERY, memberId).getResultList();
		return rows.stream()
			.map(this::toBadge)
			.toList();
	}

	private Badge toBadge(Object value) {
		Object[] row = (Object[]) value;
		return new Badge((String) row[0], asOffsetDateTime(row[1]));
	}

	private Query query(String sql, Long memberId) {
		return entityManager.createNativeQuery(sql)
			.setParameter("memberId", memberId);
	}

	private long asLong(Object value) {
		return ((Number) value).longValue();
	}

	private OffsetDateTime asOffsetDateTime(Object value) {
		if (value instanceof OffsetDateTime offsetDateTime) {
			return offsetDateTime;
		}
		if (value instanceof Instant instant) {
			return instant.atOffset(ZoneOffset.UTC);
		}
		throw new IllegalStateException(
			"Unsupported prediction badge timestamp type: "
				+ value.getClass().getName()
		);
	}
}
