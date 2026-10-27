package com.ssafy.bidlive.member.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.bidlive.member.api.dto.SettlementAccountUpdateErrorResponse;
import com.ssafy.bidlive.member.application.SettlementAccountUpdateException;
import com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 정산 계좌 등록·변경 오류 코드별 HTTP 상태와 메시지를 확인한다. */
class SettlementAccountUpdateExceptionHandlerTest {

	private final SettlementAccountUpdateExceptionHandler handler =
		new SettlementAccountUpdateExceptionHandler();

	@Test
	void mapsBadRequestConflictAndServiceUnavailable() {
		assertResponse(
			SettlementAccountUpdateErrorCode.INVALID_BANK_CODE,
			HttpStatus.BAD_REQUEST
		);
		assertResponse(
			SettlementAccountUpdateErrorCode
				.SETTLEMENT_ACCOUNT_CHANGE_BLOCKED,
			HttpStatus.CONFLICT
		);
		assertResponse(
			SettlementAccountUpdateErrorCode
				.SETTLEMENT_ACCOUNT_UPDATE_FAILED,
			HttpStatus.SERVICE_UNAVAILABLE
		);
	}

	private void assertResponse(
		SettlementAccountUpdateErrorCode errorCode,
		HttpStatus expectedStatus
	) {
		ResponseEntity<SettlementAccountUpdateErrorResponse> response =
			handler.handle(new SettlementAccountUpdateException(errorCode));

		assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo(errorCode.name());
		assertThat(response.getBody().message()).isNotBlank();
	}
}
