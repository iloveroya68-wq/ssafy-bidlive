package com.ssafy.bidlive.report.domain;

/** 한 경매에 접수된 사유별 신고 건수. */
public record ReportReasonCount(String reasonCode, int count) {
}
