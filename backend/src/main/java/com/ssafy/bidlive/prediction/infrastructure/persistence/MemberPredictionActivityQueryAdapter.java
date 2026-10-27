package com.ssafy.bidlive.prediction.infrastructure.persistence;

import com.ssafy.bidlive.prediction.application.MemberPredictionActivity;
import com.ssafy.bidlive.prediction.application.MemberPredictionActivityQuery;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 예측 정산 결과에서 환불을 제외한 회원 참여·적중 건수를 집계한다. */
@Repository
public class MemberPredictionActivityQueryAdapter
	implements MemberPredictionActivityQuery {

	private static final String QUERY = """
		select
			count(*),
			count(*) filter (where prediction_result.result_status = 'HIT')
		from prediction_result
		join prediction
		  on prediction.prediction_id = prediction_result.prediction_id
		join auction_participant
		  on auction_participant.auction_participant_id =
			prediction.auction_participant_id
		where auction_participant.member_id = :memberId
		  and auction_participant.participation_type = 'PREDICTOR'
		  and prediction_result.result_status in ('HIT', 'MISS')
		""";

	private final EntityManager entityManager;

	public MemberPredictionActivityQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public MemberPredictionActivity query(Long memberId) {
		Object[] counts = (Object[])entityManager.createNativeQuery(QUERY)
			.setParameter("memberId", memberId)
			.getSingleResult();
		return new MemberPredictionActivity(
			toLong(counts[0]),
			toLong(counts[1])
		);
	}

	private long toLong(Object value) {
		return ((Number)value).longValue();
	}
}
