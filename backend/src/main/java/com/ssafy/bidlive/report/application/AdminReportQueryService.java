package com.ssafy.bidlive.report.application;

import java.util.List;
import java.util.Map;

import com.ssafy.bidlive.report.domain.ReportReasonCount;
import com.ssafy.bidlive.report.domain.ReportedAuctionCursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADMIN-01 운영자 신고 목록 조회.
 *
 * <p>다음 장 존재는 <b>size + 1건을 읽어</b> 판정한다 — COUNT를 따로 치면 두 쿼리가 서로 다른
 * 스냅샷을 보게 되어 「다음 장이 있다는데 비어 있는」 경계가 생긴다.
 */
@Service
public class AdminReportQueryService {

	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 100;

	private final AdminReportQueryPersistence persistence;

	public AdminReportQueryService(AdminReportQueryPersistence persistence) {
		this.persistence = persistence;
	}

	@Transactional(readOnly = true)
	public ReportedAuctionPage getReportedAuctions(
		boolean includeEnded,
		ReportedAuctionCursor cursor,
		int size
	) {
		int normalized = normalizeSize(size);
		List<ReportedAuction> fetched =
			persistence.findReportedAuctions(includeEnded, cursor, normalized + 1);

		boolean hasNext = fetched.size() > normalized;
		List<ReportedAuction> page = hasNext ? fetched.subList(0, normalized) : fetched;

		Map<Long, List<ReportReasonCount>> reasons = persistence.findReasonCounts(
			page.stream().map(ReportedAuction::auctionId).toList()
		);
		List<ReportedAuction> items = page.stream()
			.map(auction -> auction
				.withReasonCounts(reasons.getOrDefault(auction.auctionId(), List.of()))
				.withMaskedSeller())
			.toList();

		return new ReportedAuctionPage(
			items,
			hasNext ? nextCursorOf(items) : null,
			hasNext
		);
	}

	/** 명세 「기본 20, 최대 100」. 0 이하는 기본값으로 되돌린다 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return DEFAULT_SIZE;
		}
		return Math.min(size, MAX_SIZE);
	}

	/** 커서는 <b>잘라낸 뒤 마지막 행</b> 기준이다. 여분 행으로 만들면 한 건이 건너뛰어진다. */
	private static String nextCursorOf(List<ReportedAuction> items) {
		ReportedAuction last = items.get(items.size() - 1);
		return new ReportedAuctionCursor(last.latestReportedAt(), last.auctionId()).encode();
	}
}
