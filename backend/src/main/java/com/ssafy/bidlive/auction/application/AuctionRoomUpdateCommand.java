package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/**
 * 경매방 부분 수정 명령이다. 모든 필드가 선택이며 {@code null}은 현재 값 유지를 뜻한다.
 *
 * <p>상품 쪽 값은 그대로 `product` 도메인 계약으로 넘기고, 경매 조건만 이 도메인이 다룬다.
 */
public record AuctionRoomUpdateCommand(
	long auctionId,
	Long memberId,
	String title,
	String description,
	String categoryCode,
	String imageUrl,
	Long reservePrice,
	Integer durationMinutes,
	String startType,
	Instant scheduledAt
) {

	public boolean hasNoProductChange() {
		return title == null && description == null && categoryCode == null && imageUrl == null;
	}

	public boolean hasNoAuctionChange() {
		return reservePrice == null
			&& durationMinutes == null
			&& startType == null
			&& scheduledAt == null;
	}

	public boolean isEmpty() {
		return hasNoProductChange() && hasNoAuctionChange();
	}
}
