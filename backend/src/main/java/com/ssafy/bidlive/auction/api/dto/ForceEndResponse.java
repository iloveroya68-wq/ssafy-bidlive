package com.ssafy.bidlive.auction.api.dto;

import java.time.Instant;

import com.ssafy.bidlive.auction.application.AuctionForceEndResult;
import com.ssafy.bidlive.auction.domain.AuctionStatus;

/** 명세 성공 응답 4필드. `resultType`은 강제 종료 고정값이다. */
public record ForceEndResponse(
	long auctionId,
	String status,
	String resultType,
	Instant endedAt
) {

	private static final String FORCED_RESULT_TYPE = "FORCED";

	public static ForceEndResponse from(AuctionForceEndResult result) {
		return new ForceEndResponse(
			result.auctionId(),
			AuctionStatus.ENDED.name(),
			FORCED_RESULT_TYPE,
			result.endedAt()
		);
	}
}
