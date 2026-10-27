package com.ssafy.bidlive.report.api;

import com.ssafy.bidlive.report.api.dto.ReportErrorResponse;
import com.ssafy.bidlive.report.application.AdminReportException;
import com.ssafy.bidlive.report.domain.AdminReportErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ADMIN-01 조회 실패를 명세의 오류 코드와 상태로 변환한다.
 *
 * <p>이게 없으면 `ResponseStatusException`의 reason이 `BasicErrorController`에서 버려져
 * 명세가 약속한 `code`가 응답에서 사라진다 — `INVALID_CURSOR`와 `INVALID_SIZE`가 구분되지 않는다.
 */
@RestControllerAdvice(assignableTypes = AdminReportController.class)
public class AdminReportExceptionHandler {

	@ExceptionHandler(AdminReportException.class)
	ResponseEntity<ReportErrorResponse> handleAdminReport(AdminReportException exception) {
		AdminReportErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(statusOf(errorCode))
			.body(new ReportErrorResponse(errorCode.name(), messageOf(errorCode)));
	}

	private static HttpStatus statusOf(AdminReportErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_CURSOR, INVALID_SIZE, INVALID_INCLUDE_ENDED -> HttpStatus.BAD_REQUEST;
		};
	}

	private static String messageOf(AdminReportErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_CURSOR -> "커서가 올바르지 않습니다.";
			case INVALID_SIZE -> "조회 개수가 올바르지 않습니다.";
			case INVALID_INCLUDE_ENDED -> "종료 포함 여부가 올바르지 않습니다.";
		};
	}
}
