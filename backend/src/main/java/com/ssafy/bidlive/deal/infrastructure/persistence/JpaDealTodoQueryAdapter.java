package com.ssafy.bidlive.deal.infrastructure.persistence;

import java.util.List;

import com.ssafy.bidlive.deal.application.DealTodoItem;
import com.ssafy.bidlive.deal.application.DealTodoQuery;
import com.ssafy.bidlive.deal.domain.DealRole;
import com.ssafy.bidlive.deal.domain.DealStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/**
 * 「지금 할 일」을 한 번의 조회로 만든다. 액션 주체 판정(상태 × 역할)을 WHERE에 두었기 때문에
 * 돌아온 행은 전부 할 일이고, 자바 쪽에서 버리는 행이 없다.
 */
@Repository
public class JpaDealTodoQueryAdapter implements DealTodoQuery {

	/**
	 * 정렬 키를 두 번 쓰는 대신 표현식을 한곳에 둔다. `deal`에는 갱신 시각 컬럼이 없어
	 * `deal_shipping.updated_at`(트리거 갱신)을 쓰고, 배송지 입력 전에는 그 행이 아예 없어
	 * `deal.created_at`으로 떨어진다.
	 */
	private static final String LAST_CHANGED_AT = "COALESCE(ds.updated_at, d.created_at)";

	private static final String SELECT_TODOS = """
		SELECT
			d.deal_id,
			ar.auction_room_id,
			p.title,
			%s,
			CASE WHEN d.buyer_member_id = :memberId THEN 'BUYER' ELSE 'SELLER' END,
			d.deal_status,
			d.final_price,
			ar.ended_at,
			%s,
			CASE WHEN d.deal_status = 'WAITING_RECEIPT' THEN ds.auto_confirm_at END
		FROM deal d
		JOIN auction_result res ON res.auction_result_id = d.auction_result_id
		JOIN auction_room ar ON ar.auction_room_id = res.auction_room_id
		JOIN product p ON p.product_id = ar.product_id
		LEFT JOIN deal_shipping ds ON ds.deal_id = d.deal_id
		WHERE (d.deal_status = 'WAITING_ADDRESS' AND d.buyer_member_id = :memberId)
			OR (d.deal_status = 'WAITING_SHIPMENT' AND d.seller_member_id = :memberId)
			OR (d.deal_status = 'WAITING_RECEIPT' AND d.buyer_member_id = :memberId)
			-- 판매자도 수령 확인까지 남는다 (S15P11A704-694). 발송 확인을 누르는 순간 목록에서
			-- 사라져서, 자기가 무엇을 어디로 보냈는지 다시 볼 데가 없었다. 이 행만 **액션이 없다** —
			-- 액션 판정이 상태만 보던 것을 역할까지 보게 바꾼 이유다(`DealTodoAction`).
			OR (d.deal_status = 'WAITING_RECEIPT' AND d.seller_member_id = :memberId)
		ORDER BY %s ASC, d.deal_id ASC
		""".formatted(DealProductThumbnail.SELECT, LAST_CHANGED_AT, LAST_CHANGED_AT);

	private final EntityManager entityManager;

	public JpaDealTodoQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public List<DealTodoItem> findActionableDeals(long memberId) {
		List<?> rows = entityManager.createNativeQuery(SELECT_TODOS)
			.setParameter("memberId", memberId)
			.getResultList();
		return rows.stream()
			.map(JpaDealTodoQueryAdapter::toItem)
			.toList();
	}

	private static DealTodoItem toItem(Object value) {
		Object[] row = (Object[]) value;
		return new DealTodoItem(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue(),
			(String) row[2],
			(String) row[3],
			DealRole.valueOf((String) row[4]),
			DealStatus.valueOf((String) row[5]),
			((Number) row[6]).longValue(),
			DealTimestamps.toInstant(row[7]),
			DealTimestamps.toInstant(row[8]),
			DealTimestamps.toInstant(row[9])
		);
	}
}
