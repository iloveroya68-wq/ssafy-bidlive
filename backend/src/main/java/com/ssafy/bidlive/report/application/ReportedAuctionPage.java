package com.ssafy.bidlive.report.application;

import java.util.List;

/** 신고 접수 경매 한 페이지. */
public record ReportedAuctionPage(
	List<ReportedAuction> items,
	String nextCursor,
	boolean hasNext
) {
}
