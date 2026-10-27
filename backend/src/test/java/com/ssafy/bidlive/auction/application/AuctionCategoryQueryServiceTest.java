package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.ssafy.bidlive.auction.domain.AuctionCategoryErrorCode;
import org.junit.jupiter.api.Test;

class AuctionCategoryQueryServiceTest {

	private final AuctionCategoryQueryPersistence persistence =
		mock(AuctionCategoryQueryPersistence.class);
	private final AuctionCategoryQueryService service =
		new AuctionCategoryQueryService(persistence);

	@Test
	void returnsActiveCategoriesFromPersistence() {
		when(persistence.findActiveCategories()).thenReturn(List.of(
			new AuctionCategoryItem(1L, "ELECTRONICS", "전자기기", 1),
			new AuctionCategoryItem(2L, "FASHION", "패션·잡화", 2)
		));

		AuctionCategoryQueryResult result = service.getCategories();

		assertThat(result.items())
			.extracting(
				AuctionCategoryItem::code,
				AuctionCategoryItem::displayOrder
			)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("ELECTRONICS", 1),
				org.assertj.core.groups.Tuple.tuple("FASHION", 2)
			);
	}

	@Test
	void returnsEmptyItemsWhenThereAreNoActiveCategories() {
		when(persistence.findActiveCategories()).thenReturn(List.of());

		assertThat(service.getCategories().items()).isEmpty();
	}

	@Test
	void convertsPersistenceFailureToServiceUnavailable() {
		when(persistence.findActiveCategories())
			.thenThrow(new IllegalStateException("database unavailable"));

		assertThatThrownBy(service::getCategories)
			.isInstanceOf(AuctionCategoryQueryException.class)
			.satisfies(exception -> assertThat(
				((AuctionCategoryQueryException) exception).getErrorCode()
			).isEqualTo(AuctionCategoryErrorCode.CATEGORY_SERVICE_UNAVAILABLE));
	}
}
