package com.ssafy.bidlive.auction.application;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionStatus;

/**
 * 입장 결과다. 대기방과 진행 중 입장의 응답 모양이 달라 쓰지 않는 값은 {@code null}로 둔다 —
 * 대기방은 {@code scheduledAt}·{@code destinations}, 진행 중은 {@code catchUpSummaryAvailable}.
 */
public record AuctionRoomJoinResult(
	Instant serverTime,
	Long auctionId,
	AuctionStatus roomStatus,
	Instant scheduledAt,
	boolean chatAllowed,
	boolean bidAllowed,
	boolean predictionAllowed,
	String roomDestination,
	String chatDestination,
	String viewersDestination,
	Boolean catchUpSummaryAvailable
) {
}
