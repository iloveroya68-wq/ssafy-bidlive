package com.ssafy.bidlive.bid.api.dto;

/** BID_ACCEPTED WebSocket 이벤트가 공개하는 입찰 집계와 기세 문구를 표현한다. */
public record BidAcceptedData(
	long bidCount,
	long participantCount,
	String vibeMessage
) {

	public BidAcceptedData {
		if (bidCount < 0L) {
			throw new IllegalArgumentException("bidCount must not be negative");
		}
		if (participantCount < 0L) {
			throw new IllegalArgumentException(
				"participantCount must not be negative"
			);
		}
		if (vibeMessage == null || vibeMessage.isBlank()) {
			throw new IllegalArgumentException("vibeMessage must not be blank");
		}
	}
}
