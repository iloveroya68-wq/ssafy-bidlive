package com.ssafy.bidlive.report.api;

import com.ssafy.bidlive.report.api.dto.ReportErrorResponse;
import com.ssafy.bidlive.report.application.AuctionReportException;
import com.ssafy.bidlive.report.domain.AuctionReportErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 신고 접수 실패를 명세의 오류 코드와 상태로 변환한다. */
@RestControllerAdvice(assignableTypes = AuctionReportController.class)
public class AuctionReportExceptionHandler {

	@ExceptionHandler(AuctionReportException.class)
	ResponseEntity<ReportErrorResponse> handleReport(AuctionReportException exception) {
		AuctionReportErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(statusOf(errorCode))
			.body(new ReportErrorResponse(errorCode.name(), messageOf(errorCode)));
	}

	private static HttpStatus statusOf(AuctionReportErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_REPORT_REQUEST -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case OWN_AUCTION_REPORT_NOT_ALLOWED -> HttpStatus.FORBIDDEN;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case AUCTION_NOT_REPORTABLE, DUPLICATE_AUCTION_REPORT -> HttpStatus.CONFLICT;
		};
	}

	private static String messageOf(AuctionReportErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_REPORT_REQUEST -> "신고 사유와 상세 설명을 다시 확인해 주세요.";
			case UNAUTHORIZED -> "로그인이 필요합니다.";
			case OWN_AUCTION_REPORT_NOT_ALLOWED -> "자신의 경매는 신고할 수 없습니다.";
			case AUCTION_NOT_FOUND -> "경매를 찾을 수 없습니다.";
			case AUCTION_NOT_REPORTABLE -> "지금은 신고할 수 없는 경매입니다.";
			case DUPLICATE_AUCTION_REPORT -> "이미 신고한 경매입니다.";
		};
	}
}
