package com.ssafy.bidlive.deal.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SOLD 경매 결과를 구매자·판매자 에스크로 거래로 연결한다. */
@Service
public class AuctionDealCreationService {

	private final AuctionDealCreationPersistence persistence;

	public AuctionDealCreationService(
		AuctionDealCreationPersistence persistence
	) {
		this.persistence = persistence;
	}

	@Transactional
	public long create(long auctionResultId) {
		if (auctionResultId <= 0) {
			throw new IllegalArgumentException(
				"auctionResultId must be positive"
			);
		}
		return persistence.createOrFind(auctionResultId)
			.orElseThrow(() -> new IllegalStateException(
				"sold auction result is required to create a deal"
			));
	}
}
