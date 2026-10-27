package com.ssafy.bidlive.report.application;

import java.time.Instant;
import java.util.Optional;

/** 경매 신고 접수 영속성 계약이다. */
public interface AuctionReportPersistence {

	Optional<AuctionReportTarget> findReportTarget(long auctionId);

	/** 같은 회원이 같은 경매에 이미 접수한 신고가 있는지 본다. */
	boolean existsReport(long auctionId, long reporterMemberId);

	/**
	 * 신고를 접수한다. 유니크 인덱스가 중복을 잡으면 아무것도 넣지 않는다.
	 *
	 * @return 접수된 신고. 이미 같은 회원이 같은 경매를 신고했으면 빈 값
	 */
	Optional<AuctionReportResult> save(long auctionId, long reporterMemberId,
		AuctionReportCommand command, Instant receivedAt);
}
