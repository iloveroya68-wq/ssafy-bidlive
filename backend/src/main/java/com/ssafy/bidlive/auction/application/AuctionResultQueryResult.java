package com.ssafy.bidlive.auction.application;

import java.util.List;

import com.ssafy.bidlive.auction.domain.AuctionResultViewerRole;

/**
 * 낙찰과 유찰을 한 타입에 담는다. 프론트도 {@code resultType}을 판별자로 쓰는 유니온이라
 * 응답 모양이 두 갈래인 것이 계약이다.
 *
 * <p>유찰이면 {@code finalPrice}·{@code bids}·{@code predictionBands}·{@code snapshotUrl}이
 * 의미를 잃는다 — 낙찰가가 없으니 밴드도 계산되지 않고, 「낙찰 순간」 스냅샷도 없다.
 */
public record AuctionResultQueryResult(
	long auctionId,
	String resultType,
	AuctionResultViewerRole viewerRole,
	String winnerLabel,
	Long finalPrice,
	BidSettlement myBidSettlement,
	PredictionSettlement myPredictionSettlement,
	List<MaskedBidEntry> bids,
	List<PredictionBandRow> predictionBands,
	Boolean canRelist,
	String snapshotUrl
) {

	public static AuctionResultQueryResult sold(
		long auctionId,
		AuctionResultViewerRole viewerRole,
		String winnerLabel,
		long finalPrice,
		BidSettlement myBidSettlement,
		PredictionSettlement myPredictionSettlement,
		List<MaskedBidEntry> bids,
		List<PredictionBandRow> predictionBands,
		String snapshotUrl
	) {
		return new AuctionResultQueryResult(
			auctionId, "SOLD", viewerRole, winnerLabel, finalPrice,
			myBidSettlement, myPredictionSettlement, bids, predictionBands, null, snapshotUrl
		);
	}

	public static AuctionResultQueryResult noSale(
		long auctionId,
		String terminationReason,
		AuctionResultViewerRole viewerRole,
		BidSettlement myBidSettlement,
		PredictionSettlement myPredictionSettlement,
		boolean canRelist
	) {
		// 유찰에는 스냅샷을 안 준다 — 「낙찰 순간」 캡처라 낙찰이 없으면 가리킬 순간도 없다
		return new AuctionResultQueryResult(
			auctionId, terminationReason, viewerRole, null, null,
			myBidSettlement, myPredictionSettlement, List.of(), List.of(), canRelist, null
		);
	}

	public boolean sold() {
		return "SOLD".equals(resultType);
	}
}
