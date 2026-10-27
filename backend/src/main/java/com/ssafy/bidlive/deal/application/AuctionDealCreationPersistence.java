package com.ssafy.bidlive.deal.application;

import java.util.OptionalLong;

/** 낙찰 결과로 에스크로 거래를 한 번만 생성하는 영속성 계약이다. */
public interface AuctionDealCreationPersistence {

	OptionalLong createOrFind(long auctionResultId);
}
