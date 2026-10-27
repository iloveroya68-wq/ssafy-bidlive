package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.BidCounts;
import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.FinalBid;
import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.ResultWrite;
import com.ssafy.bidlive.auction.domain.VickreySettlementOutcome;
import org.junit.jupiter.api.Test;

/** BID-07 PostgreSQL 포트 값의 변환과 낙찰·유찰 불변조건을 검증한다. */
class AuctionSettlementPersistenceTest {

	private static final Instant SUBMITTED_AT =
		Instant.parse("2026-07-20T12:19:00Z");

	@Test
	void convertsFinalBidToPureVickreyInput() {
		FinalBid bid = new FinalBid(
			11L,
			21L,
			31L,
			"입찰자",
			82_000L,
			SUBMITTED_AT
		);

		assertThat(bid.toVickreyBid().bidId()).isEqualTo(11L);
		assertThat(bid.toVickreyBid().memberId()).isEqualTo(31L);
		assertThat(bid.toVickreyBid().amount()).isEqualTo(82_000L);
		assertThat(bid.toVickreyBid().submittedAt())
			.isEqualTo(SUBMITTED_AT);
	}

	@Test
	void acceptsConsistentSoldAndNoSaleWrites() {
		ResultWrite sold = sold(2, 3, 74_000L);
		ResultWrite noSale = new ResultWrite(
			100L,
			VickreySettlementOutcome.NO_SALE,
			null,
			null,
			null,
			null,
			null,
			0,
			0
		);

		assertThat(sold.outcome()).isEqualTo(VickreySettlementOutcome.SOLD);
		assertThat(noSale.outcome())
			.isEqualTo(VickreySettlementOutcome.NO_SALE);
	}

	@Test
	void rejectsBidCountsAndResultShapesThatCannotBeStored() {
		assertThatThrownBy(() -> new BidCounts(2, 1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> sold(1, 2, 74_000L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("second price presence must match bidder count");
		assertThatThrownBy(() -> new ResultWrite(
			100L,
			VickreySettlementOutcome.NO_SALE,
			21L,
			null,
			null,
			null,
			null,
			0,
			0
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("no-sale result must not have bids or prices");
	}

	private static ResultWrite sold(
		int validBidderCount,
		int totalBidCount,
		Long secondHighestBidAmount
	) {
		return new ResultWrite(
			100L,
			VickreySettlementOutcome.SOLD,
			21L,
			11L,
			82_000L,
			secondHighestBidAmount,
			74_000L,
			validBidderCount,
			totalBidCount
		);
	}
}
