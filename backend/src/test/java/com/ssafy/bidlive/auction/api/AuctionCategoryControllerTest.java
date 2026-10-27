package com.ssafy.bidlive.auction.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionCategoryItem;
import com.ssafy.bidlive.auction.application.AuctionCategoryQueryException;
import com.ssafy.bidlive.auction.application.AuctionCategoryQueryResult;
import com.ssafy.bidlive.auction.application.AuctionCategoryQueryService;
import com.ssafy.bidlive.auction.domain.AuctionCategoryErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuctionCategoryControllerTest {

	private AuctionCategoryQueryService queryService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		queryService = mock(AuctionCategoryQueryService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new AuctionCategoryController(queryService)
		)
			.setControllerAdvice(new AuctionCategoryExceptionHandler())
			.build();
	}

	@Test
	void returnsCategoryListWithoutAuthentication() throws Exception {
		when(queryService.getCategories()).thenReturn(
			new AuctionCategoryQueryResult(List.of(
				new AuctionCategoryItem(
					1L,
					"ELECTRONICS",
					"전자기기",
					1
				),
				new AuctionCategoryItem(2L, "FASHION", "패션·잡화", 2)
			))
		);

		mockMvc.perform(get("/api/v1/auctions/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].categoryId").value(1))
			.andExpect(jsonPath("$.items[0].code").value("ELECTRONICS"))
			.andExpect(jsonPath("$.items[0].name").value("전자기기"))
			.andExpect(jsonPath("$.items[0].displayOrder").value(1))
			.andExpect(jsonPath("$.items[1].categoryId").value(2))
			.andExpect(jsonPath("$.items[1].code").value("FASHION"))
			.andExpect(jsonPath("$.items[1].displayOrder").value(2));
	}

	@Test
	void returnsEmptyItemsWhenNoActiveCategoryExists() throws Exception {
		when(queryService.getCategories()).thenReturn(
			new AuctionCategoryQueryResult(List.of())
		);

		mockMvc.perform(get("/api/v1/auctions/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray())
			.andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void returnsServiceUnavailableWhenCategoryQueryFails() throws Exception {
		when(queryService.getCategories()).thenThrow(
			new AuctionCategoryQueryException(
				AuctionCategoryErrorCode.CATEGORY_SERVICE_UNAVAILABLE,
				new IllegalStateException("database unavailable")
			)
		);

		mockMvc.perform(get("/api/v1/auctions/categories"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code")
				.value("CATEGORY_SERVICE_UNAVAILABLE"))
			.andExpect(jsonPath("$.message")
				.value("카테고리 목록을 조회할 수 없습니다."));
	}
}
