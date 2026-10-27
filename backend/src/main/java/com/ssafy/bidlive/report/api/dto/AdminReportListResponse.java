package com.ssafy.bidlive.report.api.dto;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.report.application.ReportedAuction;
import com.ssafy.bidlive.report.application.ReportedAuctionPage;

/** ADMIN-01 응답 봉투. */
public record AdminReportListResponse(
	List<Item> items,
	String nextCursor,
	boolean hasNext
) {

	public static AdminReportListResponse from(ReportedAuctionPage page) {
		return new AdminReportListResponse(
			page.items().stream().map(Item::from).toList(),
			page.nextCursor(),
			page.hasNext()
		);
	}

	public record Item(
		long auctionId,
		String title,
		String thumbnailUrl,
		String sellerNickname,
		String status,
		int reportCount,
		List<ReasonCount> reasonCounts,
		Instant latestReportedAt
	) {

		static Item from(ReportedAuction auction) {
			return new Item(
				auction.auctionId(),
				auction.title(),
				auction.thumbnailUrl(),
				auction.sellerNickname(),
				auction.status(),
				auction.reportCount(),
				auction.reasonCounts().stream()
					.map(count -> new ReasonCount(count.reasonCode(), count.count()))
					.toList(),
				auction.latestReportedAt()
			);
		}
	}

	public record ReasonCount(String reasonCode, int count) {
	}
}
