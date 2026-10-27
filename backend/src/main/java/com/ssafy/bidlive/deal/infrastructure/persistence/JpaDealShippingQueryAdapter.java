package com.ssafy.bidlive.deal.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.deal.application.DealShippingQuery;
import com.ssafy.bidlive.deal.application.DealShippingView;
import com.ssafy.bidlive.deal.domain.DealRole;
import com.ssafy.bidlive.deal.domain.DealStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 거래 배송 정보를 당사자 조건과 함께 한 번에 읽는다 (S15P11A704-694). */
@Repository
public class JpaDealShippingQueryAdapter implements DealShippingQuery {

	/**
	 * <b>당사자 조건이 WHERE에 있다.</b> 남의 거래는 행 자체가 안 나오므로 「읽고 나서 버리는」
	 * 경로가 없다 — 구매자의 실명·전화번호·주소가 실리는 조회라 판정이 새는 자리를 만들지 않는다.
	 *
	 * <p>`deal_shipping`은 LEFT JOIN이다. 배송지 입력 전에는 그 행이 아예 없고, 그 상태도
	 * 「아직 안 받았다」는 유효한 답이라 거래를 못 찾은 것으로 취급하면 안 된다.
	 */
	private static final String SELECT_SHIPPING = """
		SELECT
			d.deal_id,
			CASE WHEN d.buyer_member_id = :memberId THEN 'BUYER' ELSE 'SELLER' END,
			d.deal_status,
			ds.recipient_name,
			ds.recipient_phone,
			ds.postal_code,
			ds.address,
			ds.address_detail,
			ds.carrier,
			ds.tracking_number,
			ds.address_submitted_at,
			ds.shipped_at,
			ds.received_confirmed_at,
			CASE WHEN d.deal_status = 'WAITING_RECEIPT' THEN ds.auto_confirm_at END
		FROM deal d
		LEFT JOIN deal_shipping ds ON ds.deal_id = d.deal_id
		WHERE d.deal_id = :dealId
			AND (d.buyer_member_id = :memberId OR d.seller_member_id = :memberId)
		""";

	private final EntityManager entityManager;

	public JpaDealShippingQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<DealShippingView> findForParticipant(long dealId, long memberId) {
		List<?> rows = entityManager.createNativeQuery(SELECT_SHIPPING)
			.setParameter("dealId", dealId)
			.setParameter("memberId", memberId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new DealShippingView(
			((Number) row[0]).longValue(),
			DealRole.valueOf((String) row[1]),
			DealStatus.valueOf((String) row[2]),
			(String) row[3],
			(String) row[4],
			(String) row[5],
			(String) row[6],
			(String) row[7],
			(String) row[8],
			(String) row[9],
			DealTimestamps.toInstant(row[10]),
			DealTimestamps.toInstant(row[11]),
			DealTimestamps.toInstant(row[12]),
			DealTimestamps.toInstant(row[13])
		));
	}
}
