package com.ssafy.bidlive.report.api.dto;

/** 신고 API의 오류 응답이다. 프론트는 `code`로 분기하고 `message`를 그대로 띄운다. */
public record ReportErrorResponse(
	String code,
	String message
) {
}
