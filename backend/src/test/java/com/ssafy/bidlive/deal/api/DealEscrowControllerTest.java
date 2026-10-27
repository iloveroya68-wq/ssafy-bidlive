package com.ssafy.bidlive.deal.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.deal.application.DealEscrowService;
import com.ssafy.bidlive.deal.application.DealException;
import com.ssafy.bidlive.deal.application.DealTransitionResults;
import com.ssafy.bidlive.deal.application.ShippingAddressCommand;
import com.ssafy.bidlive.deal.domain.DealErrorCode;
import com.ssafy.bidlive.deal.domain.DealStatus;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 전이 3종의 응답 본문과 오류 코드별 HTTP 상태 매핑을 검증한다. */
class DealEscrowControllerTest {

	private static final String ADDRESS_PATH = "/api/v1/deals/300/shipping-address";
	private static final String SHIPMENT_PATH = "/api/v1/deals/300/shipment-confirmation";
	private static final String RECEIPT_PATH = "/api/v1/deals/300/receipt-confirmation";
	private static final String ADDRESS_BODY = """
		{
			"receiverName": "김서연",
			"contact": "010-1234-5678",
			"postalCode": "06236",
			"address": "서울시 강남구 테헤란로 1",
			"detailAddress": "101동 202호"
		}
		""";

	private DealEscrowService escrowService;
	private MockMvc mockMvc;

	/**
	 * standaloneSetup은 Spring Boot의 Jackson 자동 설정을 쓰지 않아 시각이 epoch 숫자로
	 * 직렬화된다. WRITE_DATES_AS_TIMESTAMPS 비활성은 Boot 기본값이므로 여기서 같은 값을 명시해
	 * 실제 응답과 같은 ISO-8601 형식을 검증한다.
	 */
	@BeforeEach
	void setUp() {
		escrowService = mock(DealEscrowService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new DealEscrowController(escrowService))
			.setControllerAdvice(new DealExceptionHandler())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build()))
			.build();
	}

	@Test
	void returnsWaitingShipmentAfterAddressSubmission() throws Exception {
		when(escrowService.submitShippingAddress(anyLong(), anyLong(), any()))
			.thenReturn(new DealTransitionResults.Address(
				300L,
				DealStatus.WAITING_SHIPMENT,
				Instant.parse("2026-07-21T12:00:00Z")
			));

		mockMvc.perform(patch(ADDRESS_PATH)
				.principal(authenticated("11"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(ADDRESS_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dealId").value(300))
			.andExpect(jsonPath("$.status").value("WAITING_SHIPMENT"))
			.andExpect(jsonPath("$.updatedAt").value("2026-07-21T21:00:00+09:00"));

		verify(escrowService).submitShippingAddress(
			300L,
			11L,
			new ShippingAddressCommand(
				"김서연",
				"010-1234-5678",
				"06236",
				"서울시 강남구 테헤란로 1",
				"101동 202호"
			)
		);
	}

	/** 명세에 있지만 받지 않는 `memo`가 섞여 와도 400이 되지 않는다 — Jackson이 조용히 버린다. */
	@Test
	void ignoresUnknownMemoField() throws Exception {
		when(escrowService.submitShippingAddress(anyLong(), anyLong(), any()))
			.thenReturn(new DealTransitionResults.Address(
				300L,
				DealStatus.WAITING_SHIPMENT,
				Instant.parse("2026-07-21T12:00:00Z")
			));

		mockMvc.perform(patch(ADDRESS_PATH)
				.principal(authenticated("11"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"receiverName": "김서연",
						"contact": "010-1234-5678",
						"postalCode": "06236",
						"address": "서울시 강남구",
						"detailAddress": "101동",
						"memo": "문 앞에 놓아 주세요"
					}
					"""))
			.andExpect(status().isOk());
	}

	@Test
	void returnsShippedAndAutoConfirmTimes() throws Exception {
		when(escrowService.confirmShipment(anyLong(), anyLong()))
			.thenReturn(new DealTransitionResults.Shipment(
				300L,
				DealStatus.WAITING_RECEIPT,
				Instant.parse("2026-07-22T01:00:00Z"),
				Instant.parse("2026-07-29T01:00:00Z")
			));

		mockMvc.perform(post(SHIPMENT_PATH).principal(authenticated("22")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WAITING_RECEIPT"))
			.andExpect(jsonPath("$.shippedAt").value("2026-07-22T10:00:00+09:00"))
			.andExpect(jsonPath("$.autoConfirmAt").value("2026-07-29T10:00:00+09:00"));

		verify(escrowService).confirmShipment(300L, 22L);
	}

	@Test
	void returnsPayoutAmountOnReceipt() throws Exception {
		when(escrowService.confirmReceipt(anyLong(), anyLong()))
			.thenReturn(new DealTransitionResults.Receipt(
				300L,
				DealStatus.COMPLETED,
				74_000L,
				Instant.parse("2026-07-23T09:00:00Z")
			));

		mockMvc.perform(post(RECEIPT_PATH).principal(authenticated("11")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.sellerPayoutAmount").value(74000))
			.andExpect(jsonPath("$.completedAt").value("2026-07-23T18:00:00+09:00"));
	}

	@Test
	void mapsMissingPrincipalToUnauthorized() throws Exception {
		mockMvc.perform(post(RECEIPT_PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	/** 익명 요청도 `isAuthenticated()`가 참이라 이름이 숫자가 아니라는 점으로 걸러야 한다. */
	@Test
	void mapsAnonymousPrincipalToUnauthorized() throws Exception {
		mockMvc.perform(post(RECEIPT_PATH).principal(authenticated("anonymousUser")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void mapsWrongActorToForbidden() throws Exception {
		when(escrowService.confirmShipment(anyLong(), anyLong()))
			.thenThrow(new DealException(DealErrorCode.NOT_DEAL_SELLER));

		mockMvc.perform(post(SHIPMENT_PATH).principal(authenticated("11")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_DEAL_SELLER"))
			.andExpect(jsonPath("$.message").value("이 거래의 판매자만 할 수 있습니다."));
	}

	@Test
	void mapsMissingDealToNotFound() throws Exception {
		when(escrowService.confirmReceipt(anyLong(), anyLong()))
			.thenThrow(new DealException(DealErrorCode.DEAL_NOT_FOUND));

		mockMvc.perform(post(RECEIPT_PATH).principal(authenticated("11")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DEAL_NOT_FOUND"));
	}

	@Test
	void mapsWrongStageToConflict() throws Exception {
		when(escrowService.confirmReceipt(anyLong(), anyLong()))
			.thenThrow(new DealException(DealErrorCode.INVALID_DEAL_STATUS));

		mockMvc.perform(post(RECEIPT_PATH).principal(authenticated("11")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_DEAL_STATUS"));
	}

	@Test
	void mapsInvalidAddressToBadRequest() throws Exception {
		when(escrowService.submitShippingAddress(anyLong(), anyLong(), any()))
			.thenThrow(new DealException(DealErrorCode.INVALID_SHIPPING_ADDRESS));

		mockMvc.perform(patch(ADDRESS_PATH)
				.principal(authenticated("11"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(ADDRESS_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SHIPPING_ADDRESS"));
	}

	private static Principal authenticated(String name) {
		return new UsernamePasswordAuthenticationToken(name, "n/a", List.of());
	}
}
