package com.ssafy.bidlive.report.application;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.auction.domain.SellerNicknameMask;
import com.ssafy.bidlive.report.domain.ReportReasonCount;

/**
 * 신고가 접수된 경매 한 건의 집계.
 *
 * <p>{@code reasonCounts}는 어댑터의 1차 조회에서는 비어 있고, 서비스가 2차 조회로 채운다 —
 * 한 쿼리로 중첩 목록을 만들면 행이 곱해져 {@code reportCount}가 틀어진다.
 */
public record ReportedAuction(
	long auctionId,
	String title,
	String thumbnailUrl,
	String sellerNickname,
	String status,
	int reportCount,
	Instant latestReportedAt,
	List<ReportReasonCount> reasonCounts
) {

	public ReportedAuction withReasonCounts(List<ReportReasonCount> counts) {
		return new ReportedAuction(
			auctionId, title, thumbnailUrl, sellerNickname,
			status, reportCount, latestReportedAt, List.copyOf(counts)
		);
	}

	/** 판매자 닉네임을 서버에서 가린다 — 다른 조회 API와 같은 규칙이다. */
	public ReportedAuction withMaskedSeller() {
		return new ReportedAuction(
			auctionId, title, thumbnailUrl,
			SellerNicknameMask.of(sellerNickname),
			status, reportCount, latestReportedAt, reasonCounts
		);
	}
}
