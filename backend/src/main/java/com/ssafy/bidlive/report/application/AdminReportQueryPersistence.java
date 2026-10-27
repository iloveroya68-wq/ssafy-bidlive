package com.ssafy.bidlive.report.application;

import java.util.List;
import java.util.Map;

import com.ssafy.bidlive.report.domain.ReportReasonCount;
import com.ssafy.bidlive.report.domain.ReportedAuctionCursor;

/** 운영자 신고 목록 조회 포트. */
public interface AdminReportQueryPersistence {

	/**
	 * 신고가 있는 경매를 최신순으로 읽는다. {@code reasonCounts}는 비어 있다.
	 *
	 * @param limit 다음 장 판정을 위해 size + 1을 넘긴다
	 */
	List<ReportedAuction> findReportedAuctions(
		boolean includeEnded,
		ReportedAuctionCursor cursor,
		int limit
	);

	/** 경매 id별 사유 카운트. 빈 목록이면 질의하지 않고 빈 맵을 돌려준다. */
	Map<Long, List<ReportReasonCount>> findReasonCounts(List<Long> auctionIds);
}
