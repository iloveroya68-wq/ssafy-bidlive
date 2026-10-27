package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.product.application.ProductRegistrationCommand;

/** 판매자와 상품·경매 조건을 묶은 경매방 생성 명령입니다. */
public record AuctionRoomCreationCommand(
	Long sellerId,
	ProductRegistrationCommand product,
	Long reservePrice,
	Integer durationMinutes,
	String startType,
	String scheduledAt
) {
}
