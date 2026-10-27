package com.ssafy.bidlive.auction.api.dto;

import java.time.OffsetDateTime;

/**
 * 경매방 부분 수정 요청이다. 모든 필드가 선택이며, 보내지 않은 값은 그대로 유지된다.
 *
 * <p>`product`·`auction` 블록 자체도 선택이다 — 경매 조건만 고치는 요청과 상품만 고치는 요청이
 * 모두 유효하다.
 */
public record AuctionRoomUpdateRequest(
	ProductPart product,
	AuctionPart auction
) {

	public record ProductPart(
		String title,
		String description,
		String category,
		String imageUrl
	) {
	}

	public record AuctionPart(
		Long reservePrice,
		Integer durationMinutes,
		String startType,
		OffsetDateTime scheduledAt
	) {
	}

	public String title() {
		return product == null ? null : product.title();
	}

	public String description() {
		return product == null ? null : product.description();
	}

	public String category() {
		return product == null ? null : product.category();
	}

	public String imageUrl() {
		return product == null ? null : product.imageUrl();
	}

	public Long reservePrice() {
		return auction == null ? null : auction.reservePrice();
	}

	public Integer durationMinutes() {
		return auction == null ? null : auction.durationMinutes();
	}

	public String startType() {
		return auction == null ? null : auction.startType();
	}

	public OffsetDateTime scheduledAt() {
		return auction == null ? null : auction.scheduledAt();
	}
}
