package com.ssafy.bidlive.auction.api.dto;

import java.time.Instant;

import com.ssafy.bidlive.auction.application.AuctionForceEndResult;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.auction.domain.AuctionTerminationReason;

/**
 * ROOM-09 판매자 종료 성공 응답.
 *
 * <p>해제 건수·금액을 돌려주는 이유: 판매자에게 「몇 명의 입찰이 풀렸는지」는 종료의 결과 그 자체다.
 * 운영 종료 응답(`ForceEndResponse`)이 그걸 빼고 있는 건 운영자가 볼 화면이 목록이라서다.
 */
public record SellerEndResponse(
	long auctionId,
	String status,
	String resultType,
	Instant endedAt,
	int releasedBidCount,
	long releasedBidAmount,
	int refundedPredictionCount
) {

	public static SellerEndResponse from(AuctionForceEndResult result) {
		return new SellerEndResponse(
			result.auctionId(),
			AuctionStatus.ENDED.name(),
			AuctionTerminationReason.HOST_LEFT.terminationReason(),
			result.endedAt(),
			result.releasedHoldCount(),
			result.releasedHoldAmount(),
			result.refundedPredictionCount()
		);
	}
}
