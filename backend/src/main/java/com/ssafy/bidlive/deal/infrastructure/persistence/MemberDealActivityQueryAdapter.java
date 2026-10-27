package com.ssafy.bidlive.deal.infrastructure.persistence;

import com.ssafy.bidlive.deal.application.MemberDealActivity;
import com.ssafy.bidlive.deal.application.MemberDealActivityQuery;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 거래 테이블에서 회원의 완료된 판매·구매 건수를 집계한다. */
@Repository
public class MemberDealActivityQueryAdapter implements MemberDealActivityQuery {

	private static final String QUERY = """
		select
			count(*) filter (where seller_member_id = :memberId),
			count(*) filter (where buyer_member_id = :memberId)
		from deal
		where deal_status = 'COMPLETED'
		  and (
			seller_member_id = :memberId
			or buyer_member_id = :memberId
		  )
		""";

	private final EntityManager entityManager;

	public MemberDealActivityQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public MemberDealActivity query(Long memberId) {
		Object[] counts = (Object[])entityManager.createNativeQuery(QUERY)
			.setParameter("memberId", memberId)
			.getSingleResult();
		return new MemberDealActivity(
			toLong(counts[0]),
			toLong(counts[1])
		);
	}

	private long toLong(Object value) {
		return ((Number)value).longValue();
	}
}
