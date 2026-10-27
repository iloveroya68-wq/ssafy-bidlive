package com.ssafy.bidlive.auction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 비크리 정산의 유찰·하한가·2등가와 동률 및 회원별 최종 입찰 순위를 검증한다. */
class VickreySettlementPolicyTest {

	private static final long RESERVE_PRICE = 50_000L;
	private static final Instant BASE_TIME =
		Instant.parse("2026-07-20T12:00:00Z");

	@Test
	void returnsNoSaleWhenThereAreNoValidBids() {
		VickreySettlementDecision decision =
			VickreySettlementPolicy.calculate(
				RESERVE_PRICE,
				List.of()
			);

		assertThat(decision.outcome())
			.isEqualTo(VickreySettlementOutcome.NO_SALE);
		assertThat(decision.winnerId()).isNull();
		assertThat(decision.highestBidAmount()).isNull();
		assertThat(decision.secondHighestBidAmount()).isNull();
		assertThat(decision.finalPrice()).isNull();
		assertThat(decision.priceDecisionType())
			.isEqualTo(VickreyPriceDecisionType.NO_SALE);
	}

	@Test
	void settlesSingleBidderAtReservePrice() {
		VickreySettlementDecision decision =
			VickreySettlementPolicy.calculate(
				RESERVE_PRICE,
				List.of(bid(1L, 25L, 82_000L, 1))
			);

		assertSold(
			decision,
			25L,
			82_000L,
			null,
			50_000L,
			VickreyPriceDecisionType.RESERVE_PRICE
		);
	}

	@Test
	void settlesAtSecondHighestBidAmount() {
		VickreySettlementDecision decision =
			VickreySettlementPolicy.calculate(
				RESERVE_PRICE,
				List.of(
					bid(3L, 25L, 82_000L, 3),
					bid(1L, 31L, 61_000L, 1),
					bid(2L, 42L, 74_000L, 2)
				)
			);

		assertSold(
			decision,
			25L,
			82_000L,
			74_000L,
			74_000L,
			VickreyPriceDecisionType.SECOND_PRICE
		);
	}

	@Test
	void usesReservePriceWhenSecondHighestBidIsBelowReserve() {
		VickreySettlementDecision decision =
			VickreySettlementPolicy.calculate(
				RESERVE_PRICE,
				List.of(
					bid(1L, 25L, 82_000L, 1),
					bid(2L, 31L, 49_000L, 2)
				)
			);

		assertSold(
			decision,
			25L,
			82_000L,
			49_000L,
			50_000L,
			VickreyPriceDecisionType.RESERVE_PRICE
		);
	}

	@Test
	void earlierFinalBidWinsWhenHighestAmountsAreEqual() {
		VickreySettlementDecision decision =
			VickreySettlementPolicy.calculate(
				RESERVE_PRICE,
				List.of(
					bid(2L, 31L, 82_000L, 2),
					bid(1L, 25L, 82_000L, 1)
				)
			);

		assertThat(decision.winnerId()).isEqualTo(25L);
		assertThat(decision.finalPrice()).isEqualTo(82_000L);
	}

	@Test
	void lowerBidIdWinsWhenAmountAndSubmittedAtAreEqual() {
		VickreySettlementDecision decision =
			VickreySettlementPolicy.calculate(
				RESERVE_PRICE,
				List.of(
					new VickreyBid(9L, 31L, 82_000L, BASE_TIME),
					new VickreyBid(8L, 25L, 82_000L, BASE_TIME)
				)
			);

		assertThat(decision.winnerId()).isEqualTo(25L);
	}

	@Test
	void usesOnlyEachMembersLatestBidAndItsSubmittedTime() {
		VickreySettlementDecision decision =
			VickreySettlementPolicy.calculate(
				RESERVE_PRICE,
				List.of(
					bid(1L, 25L, 60_000L, 1),
					bid(2L, 31L, 82_000L, 2),
					bid(3L, 25L, 82_000L, 3)
				)
			);

		assertThat(decision.winnerId())
			.as("회원 25의 마지막 상향 입찰 시각은 회원 31보다 늦다")
			.isEqualTo(31L);
		assertThat(decision.secondHighestBidAmount()).isEqualTo(82_000L);
	}

	@Test
	void treatsRepeatedBidsByOneMemberAsOneRankedBid() {
		VickreySettlementDecision decision =
			VickreySettlementPolicy.calculate(
				RESERVE_PRICE,
				List.of(
					bid(1L, 25L, 60_000L, 1),
					bid(2L, 25L, 82_000L, 2)
				)
			);

		assertSold(
			decision,
			25L,
			82_000L,
			null,
			50_000L,
			VickreyPriceDecisionType.RESERVE_PRICE
		);
	}

	@Test
	void rejectsInvalidInputs() {
		assertThatThrownBy(() ->
			VickreySettlementPolicy.calculate(0, List.of())
		).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("reservePrice must be positive");
		assertThatThrownBy(() ->
			VickreySettlementPolicy.calculate(RESERVE_PRICE, null)
		).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("bids must not be null");
		assertThatThrownBy(() ->
			VickreySettlementPolicy.calculate(
				RESERVE_PRICE,
				java.util.Arrays.asList((VickreyBid)null)
			)
		).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("bids must not contain null");
	}

	private static VickreyBid bid(
		long bidId,
		long memberId,
		long amount,
		long secondsAfterBase
	) {
		return new VickreyBid(
			bidId,
			memberId,
			amount,
			BASE_TIME.plusSeconds(secondsAfterBase)
		);
	}

	private static void assertSold(
		VickreySettlementDecision decision,
		long winnerId,
		long highestBidAmount,
		Long secondHighestBidAmount,
		long finalPrice,
		VickreyPriceDecisionType priceDecisionType
	) {
		assertThat(decision.outcome())
			.isEqualTo(VickreySettlementOutcome.SOLD);
		assertThat(decision.winnerId()).isEqualTo(winnerId);
		assertThat(decision.highestBidAmount())
			.isEqualTo(highestBidAmount);
		assertThat(decision.secondHighestBidAmount())
			.isEqualTo(secondHighestBidAmount);
		assertThat(decision.finalPrice()).isEqualTo(finalPrice);
		assertThat(decision.priceDecisionType())
			.isEqualTo(priceDecisionType);
	}
}
