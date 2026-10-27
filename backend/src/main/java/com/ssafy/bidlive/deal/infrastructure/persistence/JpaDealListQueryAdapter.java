package com.ssafy.bidlive.deal.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.auction.domain.SellerNicknameMask;
import com.ssafy.bidlive.deal.application.DealListCriteria;
import com.ssafy.bidlive.deal.application.DealListItem;
import com.ssafy.bidlive.deal.application.DealListQuery;
import com.ssafy.bidlive.deal.domain.DealRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/**
 * 내 거래 목록. 조인은 「지금 할 일」({@code JpaDealTodoQueryAdapter})과 같은 경로를 탄다 —
 * {@code deal → auction_result → auction_room → product}.
 *
 * <p>⚠️ 마스킹 유틸을 {@code auction.domain}에서 가져온다. 규칙이 같아야 하는 값이라 복제하지
 * 않았다 — 두 벌이 되면 같은 사람이 화면마다 다르게 가려진다.
 */
@Repository
public class JpaDealListQueryAdapter implements DealListQuery {

	/**
	 * 정렬 키. {@code deal}에는 갱신 시각이 없어 완료·취소·생성 순으로 떨어뜨린다.
	 * 커서도 같은 표현식을 쓰므로 한곳에 둔다.
	 */
	private static final String SORTED_AT = "COALESCE(d.completed_at, d.cancelled_at, d.created_at)";

	private static final String SELECT_DEALS = """
		SELECT
			d.deal_id,
			ar.auction_room_id,
			CASE WHEN d.buyer_member_id = :memberId THEN 'BUYER' ELSE 'SELLER' END,
			p.title,
			%s,
			d.final_price,
			d.deal_status,
			CASE
				WHEN d.buyer_member_id = :memberId THEN seller.nickname
				ELSE buyer.nickname
			END,
			d.completed_at,
			%s
		FROM deal d
		JOIN auction_result res ON res.auction_result_id = d.auction_result_id
		JOIN auction_room ar ON ar.auction_room_id = res.auction_room_id
		JOIN product p ON p.product_id = ar.product_id
		JOIN member seller ON seller.member_id = d.seller_member_id
		JOIN member buyer ON buyer.member_id = d.buyer_member_id
		WHERE (d.buyer_member_id = :memberId OR d.seller_member_id = :memberId)
		""".formatted(DealProductThumbnail.SELECT, SORTED_AT);

	/** 최신순. 같은 시각이면 `deal_id`로 갈라 페이지 경계가 겹치지 않게 한다 */
	private static final String ORDER_BY = " ORDER BY %s DESC, d.deal_id DESC LIMIT :limit"
		.formatted(SORTED_AT);

	private final EntityManager entityManager;

	public JpaDealListQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public List<DealListItem> findDeals(DealListCriteria criteria) {
		StringBuilder sql = new StringBuilder(SELECT_DEALS);
		if (criteria.role() == DealRole.BUYER) {
			sql.append(" AND d.buyer_member_id = :memberId");
		} else if (criteria.role() == DealRole.SELLER) {
			sql.append(" AND d.seller_member_id = :memberId");
		}
		if (criteria.status() != null) {
			sql.append(" AND d.deal_status = :status");
		}
		if (criteria.cursor() != null) {
			// 키셋. 정렬 표현식과 같은 순서로 비교해야 경계에서 행이 새거나 빠지지 않는다
			sql.append(" AND (%s, d.deal_id) < (CAST(:cursorSortedAt AS TIMESTAMPTZ), :cursorDealId)"
				.formatted(SORTED_AT));
		}
		sql.append(ORDER_BY);

		Query query = entityManager.createNativeQuery(sql.toString())
			.setParameter("memberId", criteria.memberId())
			// size + 1 — 다음 장 존재 판정용
			.setParameter("limit", criteria.size() + 1);
		if (criteria.status() != null) {
			query.setParameter("status", criteria.status());
		}
		if (criteria.cursor() != null) {
			query.setParameter("cursorSortedAt", criteria.cursor().sortedAt().toString());
			query.setParameter("cursorDealId", criteria.cursor().dealId());
		}

		List<?> rows = query.getResultList();
		return rows.stream().map(JpaDealListQueryAdapter::toItem).toList();
	}

	private static DealListItem toItem(Object value) {
		Object[] row = (Object[]) value;
		return new DealListItem(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue(),
			// 서버가 CASE로 만든 값이라 항상 BUYER/SELLER다 — DB 컬럼이 아니라 안전하다
			DealRole.valueOf((String) row[2]),
			(String) row[3],
			(String) row[4],
			((Number) row[5]).longValue(),
			(String) row[6],
			SellerNicknameMask.of((String) row[7]),
			toInstant(row[8]),
			toInstant(row[9])
		);
	}

	private static Instant toInstant(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Timestamp timestamp) {
			return timestamp.toInstant();
		}
		return (Instant) value;
	}
}
