package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 판매자 닉네임 공개 정책을 적용해 회원 도메인에 제공하는 낙찰 경매 항목이다. */
public record WonAuctionHistoryItem(
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
