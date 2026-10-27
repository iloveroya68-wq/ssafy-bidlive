package com.ssafy.bidlive.auction.application;

import java.time.Instant;
import java.util.List;

/** 서버 기동 시 다시 예약할 LIVE 경매의 확정 마감 시각을 읽는 계약이다. */
public interface AuctionSettlementDeadlineReader {

	List<Deadline> findLiveDeadlines();

	record Deadline(long auctionId, Instant endAt) {

		public Deadline {
			if (auctionId <= 0) {
				throw new IllegalArgumentException(
					"auctionId must be positive"
				);
			}
			if (endAt == null) {
				throw new IllegalArgumentException("endAt must not be null");
			}
		}
	}
}
