package com.ssafy.bidlive.auction.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.bidlive.auction.application.AuctionRoomJoinResult;
import com.ssafy.bidlive.auction.domain.AuctionStatus;

/**
 * 대기방 입장 응답이다. 대기방과 진행 중 입장의 모양이 달라 **해당 없는 필드는 아예 내보내지
 * 않는다**(`NON_NULL`) — 명세가 두 예시를 다른 필드 집합으로 정했다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuctionRoomJoinResponse(
	OffsetDateTime serverTime,
	Long auctionId,
	AuctionStatus roomStatus,
	OffsetDateTime scheduledAt,
	Permissions permissions,
	Destinations destinations,
	Boolean catchUpSummaryAvailable
) {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public record Permissions(
		boolean chat,
		boolean bid,
		boolean prediction
	) {
	}

	public record Destinations(
		String room,
		String chat,
		String viewers
	) {
	}

	public static AuctionRoomJoinResponse from(AuctionRoomJoinResult result) {
		return new AuctionRoomJoinResponse(
			atKorea(result.serverTime()),
			result.auctionId(),
			result.roomStatus(),
			atKorea(result.scheduledAt()),
			new Permissions(
				result.chatAllowed(),
				result.bidAllowed(),
				result.predictionAllowed()
			),
			result.roomDestination() == null
				? null
				: new Destinations(
					result.roomDestination(),
					result.chatDestination(),
					result.viewersDestination()
				),
			result.catchUpSummaryAvailable()
		);
	}

	private static OffsetDateTime atKorea(Instant instant) {
		return instant == null ? null : instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}
}
