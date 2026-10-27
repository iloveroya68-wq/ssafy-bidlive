package com.ssafy.bidlive.auction.api.dto;

import com.ssafy.bidlive.auction.application.AuctionRoomCreationCommand;
import com.ssafy.bidlive.product.application.ProductRegistrationCommand;

public record AuctionRoomCreationRequest(
	ProductRequest product,
	AuctionRequest auction
) {

	public AuctionRoomCreationCommand toCommand(Long sellerId) {
		return new AuctionRoomCreationCommand(
			sellerId,
			product == null ? null : product.toCommand(sellerId),
			auction == null ? null : auction.reservePrice(),
			auction == null ? null : auction.durationMinutes(),
			auction == null ? null : auction.startType(),
			auction == null ? null : auction.scheduledAt()
		);
	}

	public record ProductRequest(
		String title,
		String description,
		String category,
		String imageUrl
	) {

		private ProductRegistrationCommand toCommand(Long sellerId) {
			return new ProductRegistrationCommand(
				sellerId,
				title,
				description,
				category,
				imageUrl
			);
		}
	}

	public record AuctionRequest(
		Long reservePrice,
		Integer durationMinutes,
		String startType,
		String scheduledAt
	) {
	}
}
