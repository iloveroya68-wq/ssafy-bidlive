package com.ssafy.bidlive.auction.domain;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 유효 입찰 이력에서 회원별 최종 입찰을 골라 비크리 낙찰자와 최종가를 계산한다. */
public final class VickreySettlementPolicy {

	private static final Comparator<VickreyBid> LATEST_BID_ORDER =
		Comparator.comparing(VickreyBid::submittedAt)
			.thenComparingLong(VickreyBid::bidId);

	private static final Comparator<VickreyBid> SETTLEMENT_RANKING =
		Comparator.comparingLong(VickreyBid::amount)
			.reversed()
			.thenComparing(VickreyBid::submittedAt)
			.thenComparingLong(VickreyBid::bidId);

	private VickreySettlementPolicy() {
	}

	public static VickreySettlementDecision calculate(
		long reservePrice,
		List<VickreyBid> bids
	) {
		if (reservePrice <= 0) {
			throw new IllegalArgumentException(
				"reservePrice must be positive"
			);
		}
		if (bids == null) {
			throw new IllegalArgumentException("bids must not be null");
		}

		List<VickreyBid> rankedBids = finalBidsByMember(bids).stream()
			.sorted(SETTLEMENT_RANKING)
			.toList();
		if (rankedBids.isEmpty()) {
			return VickreySettlementDecision.noSale();
		}

		VickreyBid highestBid = rankedBids.getFirst();
		if (rankedBids.size() == 1) {
			return VickreySettlementDecision.sold(
				highestBid.memberId(),
				highestBid.amount(),
				null,
				reservePrice,
				VickreyPriceDecisionType.RESERVE_PRICE
			);
		}

		VickreyBid secondHighestBid = rankedBids.get(1);
		long finalPrice = Math.max(
			reservePrice,
			secondHighestBid.amount()
		);
		VickreyPriceDecisionType priceDecisionType =
			secondHighestBid.amount() >= reservePrice
				? VickreyPriceDecisionType.SECOND_PRICE
				: VickreyPriceDecisionType.RESERVE_PRICE;
		return VickreySettlementDecision.sold(
			highestBid.memberId(),
			highestBid.amount(),
			secondHighestBid.amount(),
			finalPrice,
			priceDecisionType
		);
	}

	private static List<VickreyBid> finalBidsByMember(
		List<VickreyBid> bids
	) {
		Map<Long, VickreyBid> finalBids = new HashMap<>();
		for (VickreyBid bid : bids) {
			if (bid == null) {
				throw new IllegalArgumentException(
					"bids must not contain null"
				);
			}
			finalBids.merge(
				bid.memberId(),
				bid,
				VickreySettlementPolicy::laterBid
			);
		}
		return List.copyOf(finalBids.values());
	}

	private static VickreyBid laterBid(
		VickreyBid first,
		VickreyBid second
	) {
		return LATEST_BID_ORDER.compare(first, second) < 0
			? second
			: first;
	}
}
