package com.ssafy.bidlive.auction.domain;

/** 회원별 최종 입찰을 순위화해 산출한 비크리 정산 결정이다. */
public record VickreySettlementDecision(
	VickreySettlementOutcome outcome,
	Long winnerId,
	Long highestBidAmount,
	Long secondHighestBidAmount,
	Long finalPrice,
	VickreyPriceDecisionType priceDecisionType
) {

	public VickreySettlementDecision {
		if (outcome == null) {
			throw new IllegalArgumentException("outcome must not be null");
		}
		if (priceDecisionType == null) {
			throw new IllegalArgumentException(
				"priceDecisionType must not be null"
			);
		}
		if (outcome == VickreySettlementOutcome.NO_SALE) {
			validateNoSale(
				winnerId,
				highestBidAmount,
				secondHighestBidAmount,
				finalPrice,
				priceDecisionType
			);
		} else {
			validateSold(
				winnerId,
				highestBidAmount,
				secondHighestBidAmount,
				finalPrice,
				priceDecisionType
			);
		}
	}

	public static VickreySettlementDecision noSale() {
		return new VickreySettlementDecision(
			VickreySettlementOutcome.NO_SALE,
			null,
			null,
			null,
			null,
			VickreyPriceDecisionType.NO_SALE
		);
	}

	public static VickreySettlementDecision sold(
		long winnerId,
		long highestBidAmount,
		Long secondHighestBidAmount,
		long finalPrice,
		VickreyPriceDecisionType priceDecisionType
	) {
		return new VickreySettlementDecision(
			VickreySettlementOutcome.SOLD,
			winnerId,
			highestBidAmount,
			secondHighestBidAmount,
			finalPrice,
			priceDecisionType
		);
	}

	private static void validateNoSale(
		Long winnerId,
		Long highestBidAmount,
		Long secondHighestBidAmount,
		Long finalPrice,
		VickreyPriceDecisionType priceDecisionType
	) {
		if (winnerId != null
			|| highestBidAmount != null
			|| secondHighestBidAmount != null
			|| finalPrice != null
			|| priceDecisionType != VickreyPriceDecisionType.NO_SALE) {
			throw new IllegalArgumentException(
				"no-sale decision must not have bid or price values"
			);
		}
	}

	private static void validateSold(
		Long winnerId,
		Long highestBidAmount,
		Long secondHighestBidAmount,
		Long finalPrice,
		VickreyPriceDecisionType priceDecisionType
	) {
		if (winnerId == null || winnerId <= 0) {
			throw new IllegalArgumentException(
				"sold decision must have a positive winnerId"
			);
		}
		if (highestBidAmount == null || highestBidAmount <= 0) {
			throw new IllegalArgumentException(
				"sold decision must have a positive highestBidAmount"
			);
		}
		if (secondHighestBidAmount != null && secondHighestBidAmount <= 0) {
			throw new IllegalArgumentException(
				"secondHighestBidAmount must be positive when present"
			);
		}
		if (finalPrice == null || finalPrice <= 0) {
			throw new IllegalArgumentException(
				"sold decision must have a positive finalPrice"
			);
		}
		if (priceDecisionType == VickreyPriceDecisionType.NO_SALE) {
			throw new IllegalArgumentException(
				"sold decision must have a sale price decision type"
			);
		}
	}
}
