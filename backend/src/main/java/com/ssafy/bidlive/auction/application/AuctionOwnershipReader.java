package com.ssafy.bidlive.auction.application;

/** 다른 도메인이 경매 판매자 소유권을 판정할 때 사용하는 공개 조회 계약이다. */
public interface AuctionOwnershipReader {

	boolean isSeller(Long auctionId, Long memberId);
}
