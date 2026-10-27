package com.ssafy.bidlive.report.domain;

/** 경매 신고 접수 오류 코드다. 명세 「오류 응답」 표의 6값을 그대로 쓴다. */
public enum AuctionReportErrorCode {
	INVALID_REPORT_REQUEST,
	UNAUTHORIZED,
	OWN_AUCTION_REPORT_NOT_ALLOWED,
	AUCTION_NOT_FOUND,
	AUCTION_NOT_REPORTABLE,
	DUPLICATE_AUCTION_REPORT
}
