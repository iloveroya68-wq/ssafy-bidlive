package com.ssafy.bidlive.product.application;

/** 경매방 생성 과정에서 상품과 대표 이미지를 등록하기 위한 명령입니다. */
public record ProductRegistrationCommand(
	Long sellerMemberId,
	String title,
	String description,
	String categoryCode,
	String imageUrl
) {
}
