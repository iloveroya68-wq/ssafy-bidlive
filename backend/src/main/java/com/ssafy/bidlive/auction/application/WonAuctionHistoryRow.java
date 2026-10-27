package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 조회 계층이 경매 결과·거래·스냅샷에서 읽어 온 낙찰 경매 한 건이다. */
public record WonAuctionHistoryRow(
	long auctionId,
	long dealId,
	String productTitle,
	String imageUrl,
	String sellerNickname,
	long finalPrice,
	long myFinalBid,
	String dealStatus,
	String snapshotUrl,
	Instant endedAt
) {
}
