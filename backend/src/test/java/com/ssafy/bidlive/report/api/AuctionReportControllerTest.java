package com.ssafy.bidlive.report.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ssafy.bidlive.report.application.AuctionReportCommand;
import com.ssafy.bidlive.report.application.AuctionReportException;
import com.ssafy.bidlive.report.application.AuctionReportResult;
import com.ssafy.bidlive.report.application.AuctionReportService;
import com.ssafy.bidlive.report.domain.AuctionReportErrorCode;
import com.ssafy.bidlive.report.domain.AuctionReportReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 201 응답 계약과 오류 코드별 HTTP 상태 매핑을 검증한다. */
class AuctionReportControllerTest {

	private static final String PATH = "/api/v1/auctions/100/reports";
	private static final String BODY = """
		{
			"reasonCode": "INAPPROPRIATE_BROADCAST",
			"description": "판매자가 방송 중 부적절한 발언을 반복했습니다."
		}
		""";

	private AuctionReportService reportService;
	private MockMvc mockMvc;

	/** 시각 직렬화 형식을 실제 응답과 맞춘다 — standaloneSetup은 Boot의 Jackson 설정을 안 쓴다. */
	@BeforeEach
	void setUp() {
		reportService = mock(AuctionReportService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new AuctionReportController(reportService))
			.setControllerAdvice(new AuctionReportExceptionHandler())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build()))
			.build();
	}

	@Test
	void returnsCreatedWithReceiptOnly() throws Exception {
		when(reportService.report(anyLong(), anyLong(), any())).thenReturn(
			new AuctionReportResult(123L, "RECEIVED", Instant.parse("2026-07-22T06:00:00Z"))
		);

		mockMvc.perform(post(PATH)
				.principal(authenticated("42"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.reportId").value(123))
			.andExpect(jsonPath("$.status").value("RECEIVED"))
			.andExpect(jsonPath("$.receivedAt").value("2026-07-22T15:00:00+09:00"));

		verify(reportService).report(100L, 42L, new AuctionReportCommand(
			AuctionReportReason.INAPPROPRIATE_BROADCAST,
			"판매자가 방송 중 부적절한 발언을 반복했습니다."
		));
	}

	/** 명세에 있지만 받지 않는 `evidenceUrls`가 섞여 와도 400이 되지 않는다. */
	@Test
	void ignoresUnknownEvidenceUrlsField() throws Exception {
		when(reportService.report(anyLong(), anyLong(), any())).thenReturn(
			new AuctionReportResult(123L, "RECEIVED", Instant.parse("2026-07-22T06:00:00Z"))
		);

		mockMvc.perform(post(PATH)
				.principal(authenticated("42"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"reasonCode": "FALSE_LISTING",
						"description": null,
						"evidenceUrls": ["https://cdn.example.com/evidence/1.jpg"]
					}
					"""))
			.andExpect(status().isCreated());
	}

	@Test
	void mapsMissingPrincipalToUnauthorized() throws Exception {
		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void mapsAnonymousPrincipalToUnauthorized() throws Exception {
		mockMvc.perform(post(PATH)
				.principal(authenticated("anonymousUser"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void mapsOwnAuctionToForbidden() throws Exception {
		givenServiceFails(AuctionReportErrorCode.OWN_AUCTION_REPORT_NOT_ALLOWED);

		mockMvc.perform(post(PATH)
				.principal(authenticated("42"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("OWN_AUCTION_REPORT_NOT_ALLOWED"))
			.andExpect(jsonPath("$.message").value("자신의 경매는 신고할 수 없습니다."));
	}

	@Test
	void mapsMissingAuctionToNotFound() throws Exception {
		givenServiceFails(AuctionReportErrorCode.AUCTION_NOT_FOUND);

		mockMvc.perform(post(PATH)
				.principal(authenticated("42"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
	}

	@Test
	void mapsNotReportableToConflict() throws Exception {
		givenServiceFails(AuctionReportErrorCode.AUCTION_NOT_REPORTABLE);

		mockMvc.perform(post(PATH)
				.principal(authenticated("42"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AUCTION_NOT_REPORTABLE"));
	}

	@Test
	void mapsDuplicateToConflict() throws Exception {
		givenServiceFails(AuctionReportErrorCode.DUPLICATE_AUCTION_REPORT);

		mockMvc.perform(post(PATH)
				.principal(authenticated("42"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_AUCTION_REPORT"));
	}

	/** 모르는 사유 코드는 요청 DTO 변환 단계에서 걸려 400 `INVALID_REPORT_REQUEST`가 나간다. */
	@Test
	void mapsUnknownReasonCodeToBadRequest() throws Exception {
		mockMvc.perform(post(PATH)
				.principal(authenticated("42"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "reasonCode": "NOPE", "description": "설명" }
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REPORT_REQUEST"));
	}

	private void givenServiceFails(AuctionReportErrorCode errorCode) {
		when(reportService.report(anyLong(), anyLong(), any()))
			.thenThrow(new AuctionReportException(errorCode));
	}

	private static Principal authenticated(String name) {
		return new UsernamePasswordAuthenticationToken(name, "n/a", List.of());
	}
}
