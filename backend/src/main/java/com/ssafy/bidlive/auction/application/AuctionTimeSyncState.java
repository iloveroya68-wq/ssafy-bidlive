package com.ssafy.bidlive.auction.application;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionStatus;

/** 시간 보정 발행 여부를 판단하는 데 필요한 실시간 경매 상태다. */
public record AuctionTimeSyncState(
	AuctionStatus status,
	Instant endAt
) {
}
