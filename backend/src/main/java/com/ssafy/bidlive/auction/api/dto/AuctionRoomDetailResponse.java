package com.ssafy.bidlive.auction.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.auction.application.AuctionRoomDetailResult;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 경매방 입장 전에 공개하는 상품·판매자·경매 조건 응답입니다. */
public record AuctionRoomDetailResponse(
	OffsetDateTime serverTime,
	long auctionId,
	String title,
	AuctionStatus status,
	long reservePrice,
	OffsetDateTime scheduledAt,
	int durationMinutes,
	int viewerCount,
	@JsonProperty("isSeller") boolean seller,
	Integer notificationCount,
	ProductResponse product,
	@JsonProperty("seller") SellerResponse sellerInfo,
	boolean canEnter
) {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public static AuctionRoomDetailResponse from(AuctionRoomDetailResult result) {
		return new AuctionRoomDetailResponse(
			atKorea(result.serverTime()),
			result.auctionId(),
			result.title(),
			result.status(),
			result.reservePrice(),
			atKorea(result.scheduledAt()),
			result.durationMinutes(),
			result.viewerCount(),
			result.seller(),
			result.notificationCount(),
			ProductResponse.from(result.product()),
			SellerResponse.from(result.sellerInfo()),
			result.canEnter()
		);
	}

	private static OffsetDateTime atKorea(Instant instant) {
		return instant == null
			? null
			: instant.atZone(KOREA_ZONE).toOffsetDateTime();
	}

	/**
	 * `categoryCode`는 ROOM-02 목록과 같은 `product_category.code` 값이다. 방 수정 폼이
	 * 딥링크·새로고침으로 열려도 현재 카테고리를 채울 수 있게 상세 응답에 포함한다.
	 */
	public record ProductResponse(
		long productId,
		String title,
		String description,
		String categoryCode,
		String imageUrl
	) {

		private static ProductResponse from(
			AuctionRoomDetailResult.Product product
		) {
			return new ProductResponse(
				product.productId(),
				product.title(),
				product.description(),
				product.categoryCode(),
				product.imageUrl()
			);
		}
	}

	public record SellerResponse(
		String nickname,
		Double rating
	) {

		private static SellerResponse from(
			AuctionRoomDetailResult.Seller seller
		) {
			return new SellerResponse(seller.nickname(), seller.rating());
		}
	}
}
