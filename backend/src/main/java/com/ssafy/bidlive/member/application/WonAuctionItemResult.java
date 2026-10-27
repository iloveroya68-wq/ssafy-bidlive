package com.ssafy.bidlive.member.application;

import java.time.Instant;

/** 구매자 액션까지 계산한 마이페이지 낙찰 경매 항목이다. */
public record WonAuctionItemResult(
	long auctionId,
	long dealId,
	String productTitle,
	String imageUrl,
	String sellerNickname,
	long finalPrice,
	long myFinalBid,
	String dealStatus,
	boolean actionRequired,
	String actionCode,
	String actionLabel,
	String snapshotUrl,
	Instant endedAt
) {
}
