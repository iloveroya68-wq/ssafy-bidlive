package com.ssafy.bidlive.deal.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.deal.application.DealTodoItem;
import com.ssafy.bidlive.deal.application.DealTodoService;
import com.ssafy.bidlive.deal.domain.DealRole;
import com.ssafy.bidlive.deal.domain.DealStatus;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 할 일 응답의 액션 문구·경로 생성과 빈 목록 계약을 검증한다. */
class DealTodoControllerTest {

	private static final String PATH = "/api/v1/members/me/deals/todos";

	private DealTodoService todoService;
	private MockMvc mockMvc;

	/** 시각 직렬화 형식을 실제 응답과 맞춘다 — 이유는 {@code DealEscrowControllerTest} 참조. */
	@BeforeEach
	void setUp() {
		todoService = mock(DealTodoService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new DealTodoController(todoService))
			.setControllerAdvice(new DealExceptionHandler())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build()))
			.build();
	}

	/** 버튼 문구·안내·이동 경로는 서버가 채운다 — 화면이 상태로 문구를 짜지 않는다. */
	@Test
	void fillsActionCodeLabelAndPathFromStatus() throws Exception {
		when(todoService.findMyTodos(anyLong())).thenReturn(List.of(
			todo(300L, DealRole.BUYER, DealStatus.WAITING_ADDRESS, null),
			todo(301L, DealRole.SELLER, DealStatus.WAITING_SHIPMENT, null),
			todo(302L, DealRole.BUYER, DealStatus.WAITING_RECEIPT,
				Instant.parse("2026-07-27T01:00:00Z"))
		));

		mockMvc.perform(get(PATH).principal(authenticated("11")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(3))
			.andExpect(jsonPath("$.items[0].actionCode").value("SUBMIT_ADDRESS"))
			.andExpect(jsonPath("$.items[0].actionLabel").value("배송지 입력"))
			.andExpect(jsonPath("$.items[0].actionPath").value("/deals/300/address"))
			.andExpect(jsonPath("$.items[0].autoConfirmAt").doesNotExist())
			.andExpect(jsonPath("$.items[1].actionCode").value("CONFIRM_SHIPMENT"))
			.andExpect(jsonPath("$.items[1].actionPath").value("/deals/301/shipment"))
			.andExpect(jsonPath("$.items[1].role").value("SELLER"))
			.andExpect(jsonPath("$.items[2].actionCode").value("CONFIRM_RECEIPT"))
			.andExpect(jsonPath("$.items[2].actionPath").value("/deals/302/receipt"))
			.andExpect(jsonPath("$.items[2].autoConfirmAt").value("2026-07-27T10:00:00+09:00"));

		verify(todoService).findMyTodos(11L);
	}

	@Test
	void returnsZeroCountAndEmptyArrayWhenNothingToDo() throws Exception {
		when(todoService.findMyTodos(anyLong())).thenReturn(List.of());

		mockMvc.perform(get(PATH).principal(authenticated("11")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(0))
			.andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void mapsMissingPrincipalToUnauthorized() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private static DealTodoItem todo(
		long dealId,
		DealRole role,
		DealStatus status,
		Instant autoConfirmAt
	) {
		return new DealTodoItem(
			dealId,
			100L,
			"키크론 Q1 Pro",
			"https://cdn.example.com/products/abc.jpg",
			role,
			status,
			74_000L,
			Instant.parse("2026-07-21T11:17:13Z"),
			Instant.parse("2026-07-21T11:18:00Z"),
			autoConfirmAt
		);
	}

	private static Principal authenticated(String name) {
		return new UsernamePasswordAuthenticationToken(name, "n/a", List.of());
	}
}
