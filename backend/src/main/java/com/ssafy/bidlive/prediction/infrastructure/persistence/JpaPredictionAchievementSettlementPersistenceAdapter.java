package com.ssafy.bidlive.prediction.infrastructure.persistence;

import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionAchievementSettlementPersistence;
import com.ssafy.bidlive.prediction.domain.PredictionAchievementGrade;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 회원별 예측 업적 행을 잠가 누적 점수를 갱신하고 배지를 멱등 저장한다. */
@Repository
public class JpaPredictionAchievementSettlementPersistenceAdapter
	implements PredictionAchievementSettlementPersistence {

	private static final String CREATE_GRADE_QUERY = """
		INSERT INTO prediction_achievement (
			member_id,
			grade_code,
			grade_score
		)
		VALUES (:memberId, :gradeCode, 0)
		ON CONFLICT (member_id) DO NOTHING
		""";

	private static final String LOCK_GRADE_QUERY = """
		SELECT grade_code, grade_score
		FROM prediction_achievement
		WHERE member_id = :memberId
		FOR UPDATE
		""";

	private static final String UPDATE_GRADE_QUERY = """
		UPDATE prediction_achievement
		SET
			grade_code = :gradeCode,
			grade_score = :gradeScore
		WHERE member_id = :memberId
		""";

	private static final String PROGRESS_QUERY = """
		SELECT
			COUNT(*) FILTER (
				WHERE result.result_status IN ('HIT', 'MISS')
			),
			COUNT(*) FILTER (
				WHERE result.result_status = 'HIT'
			),
			COUNT(*) FILTER (
				WHERE result.result_status = 'HIT'
					AND result.reward_multiplier = 5
			)
		FROM prediction_result result
		JOIN prediction
			ON prediction.prediction_id = result.prediction_id
		JOIN auction_participant participant
			ON participant.auction_participant_id =
				prediction.auction_participant_id
		WHERE participant.member_id = :memberId
		""";

	private static final String INSERT_BADGE_QUERY = """
		INSERT INTO prediction_badge (
			member_id,
			badge_code,
			earned_prediction_result_id,
			earned_at
		)
		VALUES (
			:memberId,
			:badgeCode,
			:predictionResultId,
			CURRENT_TIMESTAMP
		)
		ON CONFLICT (member_id, badge_code) DO NOTHING
		""";

	private final EntityManager entityManager;

	public JpaPredictionAchievementSettlementPersistenceAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public GradeState lockOrCreateGrade(Long memberId) {
		entityManager.createNativeQuery(CREATE_GRADE_QUERY)
			.setParameter("memberId", memberId)
			.setParameter(
				"gradeCode",
				PredictionAchievementGrade.ORACLE_1.name()
			)
			.executeUpdate();
		List<?> rows = entityManager.createNativeQuery(LOCK_GRADE_QUERY)
			.setParameter("memberId", memberId)
			.getResultList();
		if (rows.size() != 1) {
			throw new IllegalStateException(
				"Prediction achievement grade row was not available"
			);
		}
		Object[] row = (Object[]) rows.getFirst();
		return new GradeState((String) row[0], asLong(row[1]));
	}

	@Override
	public void updateGrade(
		Long memberId,
		String gradeCode,
		long gradeScore
	) {
		int updated = entityManager.createNativeQuery(UPDATE_GRADE_QUERY)
			.setParameter("memberId", memberId)
			.setParameter("gradeCode", gradeCode)
			.setParameter("gradeScore", gradeScore)
			.executeUpdate();
		if (updated != 1) {
			throw new IllegalStateException(
				"Prediction achievement grade update failed"
			);
		}
	}

	@Override
	public Progress findProgress(Long memberId) {
		Object[] row = (Object[]) entityManager
			.createNativeQuery(PROGRESS_QUERY)
			.setParameter("memberId", memberId)
			.getSingleResult();
		return new Progress(
			asLong(row[0]),
			asLong(row[1]),
			asLong(row[2])
		);
	}

	@Override
	public boolean saveBadgeIfAbsent(
		Long memberId,
		String badgeCode,
		Long predictionResultId
	) {
		return entityManager.createNativeQuery(INSERT_BADGE_QUERY)
			.setParameter("memberId", memberId)
			.setParameter("badgeCode", badgeCode)
			.setParameter("predictionResultId", predictionResultId)
			.executeUpdate() == 1;
	}

	private long asLong(Object value) {
		return ((Number) value).longValue();
	}
}
