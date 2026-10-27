package com.ssafy.bidlive.member.api.dto;

import java.time.Instant;

import com.ssafy.bidlive.member.application.HostedAuctionItemResult;

/** 개최 경매 목록의 개별 경매 응답이다. */
public record HostedAuctionItemResponse(
	long auctionId,
	Long dealId,
	String productTitle,
	String imageUrl,
	String roomStatus,
	String resultType,
	Long finalPrice,
	String winnerNickname,
	String dealStatus,
	boolean actionRequired,
	String actionCode,
	String actionLabel,
	long bidCount,
	long participantCount,
	long predictionParticipantCount,
	Instant scheduledAt,
	Instant startedAt,
	Instant endedAt
) {

	public static HostedAuctionItemResponse from(HostedAuctionItemResult result) {
		return new HostedAuctionItemResponse(
			result.auctionId(),
			result.dealId(),
			result.productTitle(),
			result.imageUrl(),
			result.roomStatus(),
			result.resultType(),
			result.finalPrice(),
			result.winnerNickname(),
			result.dealStatus(),
			result.actionRequired(),
			result.actionCode(),
			result.actionLabel(),
			result.bidCount(),
			result.participantCount(),
			result.predictionParticipantCount(),
			result.scheduledAt(),
			result.startedAt(),
			result.endedAt()
		);
	}
}
