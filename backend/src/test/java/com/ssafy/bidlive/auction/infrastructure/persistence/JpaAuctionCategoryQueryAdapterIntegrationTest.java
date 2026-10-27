package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionCategoryItem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class JpaAuctionCategoryQueryAdapterIntegrationTest {

	@Autowired
	private JpaAuctionCategoryQueryAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@Test
	void returnsOnlyActiveCategoriesInDisplayOrder() {
		entityManager.createNativeQuery("""
			INSERT INTO product_category (
				code,
				name,
				is_active,
				display_order
			)
			VALUES
				('TEST_LATE', '뒤 카테고리', TRUE, 902),
				('TEST_INACTIVE', '비활성 카테고리', FALSE, 900),
				('TEST_EARLY', '앞 카테고리', TRUE, 901)
			""").executeUpdate();
		entityManager.flush();

		List<AuctionCategoryItem> testItems = adapter.findActiveCategories()
			.stream()
			.filter(item -> item.code().startsWith("TEST_"))
			.toList();

		assertThat(testItems)
			.extracting(
				AuctionCategoryItem::code,
				AuctionCategoryItem::displayOrder
			)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("TEST_EARLY", 901),
				org.assertj.core.groups.Tuple.tuple("TEST_LATE", 902)
			);
	}
}
