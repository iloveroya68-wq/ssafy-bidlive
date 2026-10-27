package com.ssafy.bidlive.member.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.bidlive.member.api.dto.SettlementAccountQueryErrorResponse;
import com.ssafy.bidlive.member.application.SettlementAccountQueryException;
import com.ssafy.bidlive.member.domain.SettlementAccountQueryErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 정산 계좌 저장소 장애가 명세의 503 오류로 변환되는지 검증한다. */
class SettlementAccountQueryExceptionHandlerTest {

	@Test
	void returnsServiceUnavailableForQueryFailure() {
		SettlementAccountQueryExceptionHandler handler =
			new SettlementAccountQueryExceptionHandler();

		ResponseEntity<SettlementAccountQueryErrorResponse> response =
			handler.handle(new SettlementAccountQueryException(
				SettlementAccountQueryErrorCode
					.SETTLEMENT_ACCOUNT_UNAVAILABLE
			));

		assertThat(response.getStatusCode())
			.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody())
			.extracting(
				SettlementAccountQueryErrorResponse::code,
				SettlementAccountQueryErrorResponse::message
			)
			.containsExactly(
				"SETTLEMENT_ACCOUNT_UNAVAILABLE",
				"정산 계좌 정보를 조회하지 못했습니다."
			);
	}
}
