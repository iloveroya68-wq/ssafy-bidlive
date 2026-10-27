package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionCategoryItem;
import com.ssafy.bidlive.auction.application.AuctionCategoryQueryPersistence;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** PostgreSQL의 상품 카테고리 마스터를 경매방 조회 모델로 변환한다. */
@Repository
public class JpaAuctionCategoryQueryAdapter
	implements AuctionCategoryQueryPersistence {

	private static final String ACTIVE_CATEGORIES_QUERY = """
		SELECT category_id, code, name, display_order
		FROM product_category
		WHERE is_active = TRUE
		ORDER BY display_order ASC, category_id ASC
		""";

	private final EntityManager entityManager;

	public JpaAuctionCategoryQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public List<AuctionCategoryItem> findActiveCategories() {
		List<?> rows = entityManager.createNativeQuery(ACTIVE_CATEGORIES_QUERY)
			.getResultList();
		return rows.stream()
			.map(this::toItem)
			.toList();
	}

	private AuctionCategoryItem toItem(Object value) {
		Object[] row = (Object[]) value;
		return new AuctionCategoryItem(
			((Number) row[0]).longValue(),
			(String) row[1],
			(String) row[2],
			((Number) row[3]).intValue()
		);
	}
}
