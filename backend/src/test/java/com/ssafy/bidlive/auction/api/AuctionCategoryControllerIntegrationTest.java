package com.ssafy.bidlive.auction.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 Security, Flyway, PostgreSQL을 거쳐 공개 카테고리 API 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class AuctionCategoryControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsSeededActiveCategoriesInDisplayOrderWithoutAuthentication()
		throws Exception {
		mockMvc.perform(get("/api/v1/auctions/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(6))
			.andExpect(jsonPath("$.items[0].code").value("ELECTRONICS"))
			.andExpect(jsonPath("$.items[0].name").value("전자기기"))
			.andExpect(jsonPath("$.items[0].displayOrder").value(1))
			.andExpect(jsonPath("$.items[1].code").value("HOBBY"))
			.andExpect(jsonPath("$.items[1].displayOrder").value(2))
			.andExpect(jsonPath("$.items[2].code").value("FASHION"))
			.andExpect(jsonPath("$.items[2].displayOrder").value(3))
			.andExpect(jsonPath("$.items[3].code").value("LIVING"))
			.andExpect(jsonPath("$.items[3].displayOrder").value(4))
			.andExpect(jsonPath("$.items[4].code").value("MEDIA"))
			.andExpect(jsonPath("$.items[4].displayOrder").value(5))
			.andExpect(jsonPath("$.items[5].code").value("SPORTS"))
			.andExpect(jsonPath("$.items[5].displayOrder").value(6));
	}
}
