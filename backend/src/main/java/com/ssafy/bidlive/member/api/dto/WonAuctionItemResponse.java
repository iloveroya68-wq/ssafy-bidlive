package com.ssafy.bidlive.member.api.dto;

import java.time.Instant;

import com.ssafy.bidlive.member.application.WonAuctionItemResult;

/** 낙찰 받은 경매 목록의 개별 경매 응답이다. */
public record WonAuctionItemResponse(
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

	public static WonAuctionItemResponse from(WonAuctionItemResult result) {
		return new WonAuctionItemResponse(
			result.auctionId(),
			result.dealId(),
			result.productTitle(),
			result.imageUrl(),
			result.sellerNickname(),
			result.finalPrice(),
			result.myFinalBid(),
			result.dealStatus(),
			result.actionRequired(),
			result.actionCode(),
			result.actionLabel(),
			result.snapshotUrl(),
			result.endedAt()
		);
	}
}
