package com.ssafy.bidlive.product.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ssafy.bidlive.product.application.ProductInspectionRejectedException;
import com.ssafy.bidlive.product.application.ProductInspectionService;
import com.ssafy.bidlive.product.domain.InspectionCategory;
import com.ssafy.bidlive.product.domain.InspectionUnitResult;
import com.ssafy.bidlive.product.domain.InspectionVerdict;
import com.ssafy.bidlive.product.domain.ProductInspection;
import com.ssafy.bidlive.product.domain.ProductInspectionFailureCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 검열 요청 계약과 판정 응답 형식, 입력·인증·Credit 실패의 상태 코드를 검증합니다. */
class ProductInspectionControllerTest {

	private static final String PATH = "/api/v1/ai/products/inspect";
	private static final OffsetDateTime INSPECTED_AT =
		OffsetDateTime.parse("2026-07-20T21:00:00+09:00");

	private final ProductInspectionService productInspectionService =
		mock(ProductInspectionService.class);

	/** standaloneSetup은 Boot의 Jackson 설정을 쓰지 않으므로 시각 직렬화 기준을 맞춘다. */
	private final MockMvc mockMvc = MockMvcBuilders
		.standaloneSetup(new ProductInspectionController(productInspectionService))
		.setControllerAdvice(new ProductInspectionExceptionHandler())
		.setMessageConverters(new MappingJackson2HttpMessageConverter(
			Jackson2ObjectMapperBuilder.json()
				.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build()))
		.build();

	@Test
	void returnsVerdictWithTextAndImageResults() throws Exception {
		when(productInspectionService.inspect(eq(25L), any())).thenReturn(new ProductInspection(
			25L,
			"a".repeat(64),
			InspectionVerdict.REJECTED,
			InspectionUnitResult.judged(
				InspectionVerdict.PASSED, null, List.of(), 0.1, "정상 텍스트"),
			List.of(InspectionUnitResult.judged(
				InspectionVerdict.REJECTED,
				"https://img/a.jpg",
				List.of(InspectionCategory.ILLEGAL_GOODS),
				0.93,
				"금지 물품"
			)),
			INSPECTED_AT
		));

		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"상품","description":"설명","imageUrls":["https://img/a.jpg"]}
					""")
				.principal(authenticated("25")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value("AI_INSPECTION_RESULT"))
			.andExpect(jsonPath("$.data.verdict").value("REJECTED"))
			.andExpect(jsonPath("$.data.textResult.verdict").value("PASSED"))
			.andExpect(jsonPath("$.data.imageResults[0].imageUrl").value("https://img/a.jpg"))
			.andExpect(jsonPath("$.data.imageResults[0].verdict").value("REJECTED"))
			.andExpect(jsonPath("$.data.imageResults[0].flaggedCategories[0]")
				.value("ILLEGAL_GOODS"))
			.andExpect(jsonPath("$.data.imageResults[0].maxScore").value(0.93))
			.andExpect(jsonPath("$.data.inspectedAt").value("2026-07-20T21:00:00+09:00"));
	}

	/** 검열 실패로 보류된 경우에도 200으로 판정을 돌려주고 사유를 노출한다. */
	@Test
	void returnsNeedsReviewWithFailureReason() throws Exception {
		when(productInspectionService.inspect(eq(25L), any())).thenReturn(new ProductInspection(
			25L,
			"b".repeat(64),
			InspectionVerdict.NEEDS_REVIEW,
			InspectionUnitResult.heldForReview(null, "TIMEOUT"),
			List.of(),
			INSPECTED_AT
		));

		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"상품\"}")
				.principal(authenticated("25")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.verdict").value("NEEDS_REVIEW"))
			.andExpect(jsonPath("$.data.textResult.failureReason").value("TIMEOUT"));
	}

	@Test
	void rejectsRequestWithoutAuthentication() throws Exception {
		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"상품\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		verify(productInspectionService, never()).inspect(any(), any());
	}

	@Test
	void rejectsRequestWithNothingToInspect() throws Exception {
		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"  \",\"imageUrls\":[]}")
				.principal(authenticated("25")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INSPECTION_REQUEST"));

		verify(productInspectionService, never()).inspect(any(), any());
	}

	@Test
	void rejectsMalformedBody() throws Exception {
		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":")
				.principal(authenticated("25")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INSPECTION_REQUEST"));
	}

	@Test
	void returnsBadGatewayWhenCreditIsExhausted() throws Exception {
		when(productInspectionService.inspect(eq(25L), any())).thenThrow(
			new ProductInspectionRejectedException(
				ProductInspectionFailureCode.AI_CREDIT_EXHAUSTED));

		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"상품\"}")
				.principal(authenticated("25")))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("AI_CREDIT_EXHAUSTED"));
	}

	@Test
	void rejectsAuthenticationWithoutNumericMemberId() throws Exception {
		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"상품\"}")
				.principal(authenticated("not-a-member-id")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private Authentication authenticated(String name) {
		return UsernamePasswordAuthenticationToken.authenticated(name, null, List.of());
	}
}
