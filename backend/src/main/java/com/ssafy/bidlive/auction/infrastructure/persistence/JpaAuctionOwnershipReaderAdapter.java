package com.ssafy.bidlive.auction.infrastructure.persistence;

import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL의 경매방과 상품 관계를 조회해 판매자 소유권을 판정한다. */
@Repository
public class JpaAuctionOwnershipReaderAdapter implements AuctionOwnershipReader {

	private static final String OWNERSHIP_QUERY = """
		SELECT EXISTS (
			SELECT 1
			FROM auction_room ar
			JOIN product p
				ON p.product_id = ar.product_id
			WHERE ar.auction_room_id = :auctionId
				AND p.seller_member_id = :memberId
		)
		""";

	private final EntityManager entityManager;

	public JpaAuctionOwnershipReaderAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isSeller(Long auctionId, Long memberId) {
		Object result = entityManager.createNativeQuery(OWNERSHIP_QUERY)
			.setParameter("auctionId", auctionId)
			.setParameter("memberId", memberId)
			.getSingleResult();

		return Boolean.TRUE.equals(result);
	}
}
