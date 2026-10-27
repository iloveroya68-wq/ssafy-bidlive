package com.ssafy.bidlive.auction.application;

import java.util.Objects;

import com.ssafy.bidlive.auction.domain.AuctionStatus;
import org.springframework.stereotype.Service;

/** 실시간 상태가 LIVE이고 마감 시각이 완전한 경매만 시간 보정 이벤트를 발행한다. */
@Service
public class AuctionTimeSyncService {

	private final AuctionTimeSyncStateReader stateReader;
	private final AuctionTimeSyncPublisher publisher;

	public AuctionTimeSyncService(
		AuctionTimeSyncStateReader stateReader,
		AuctionTimeSyncPublisher publisher
	) {
		this.stateReader = Objects.requireNonNull(stateReader, "stateReader");
		this.publisher = Objects.requireNonNull(publisher, "publisher");
	}

	public boolean synchronizeIfLive(long auctionId) {
		AuctionTimeSyncState state = stateReader.findByAuctionId(auctionId)
			.orElse(null);
		if (state == null || state.status() != AuctionStatus.LIVE) {
			return false;
		}

		publisher.publish(auctionId, state.endAt());
		return true;
	}
}
