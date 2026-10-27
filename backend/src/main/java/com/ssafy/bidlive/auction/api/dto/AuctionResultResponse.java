package com.ssafy.bidlive.auction.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.bidlive.auction.application.AuctionResultQueryResult;
import com.ssafy.bidlive.auction.application.BidSettlement;
import com.ssafy.bidlive.auction.application.MaskedBidEntry;
import com.ssafy.bidlive.auction.application.PredictionSettlement;

/**
 * 낙찰과 유찰의 필드 집합이 다르다. {@code @JsonInclude(NON_NULL)}로 <b>없는 필드는 아예 안 보낸다</b> —
 * 프론트가 {@code resultType}으로 분기하는 유니온이라 유찰 응답에 {@code finalPrice: null}이 섞이면
 * 타입 가드가 무의미해진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuctionResultResponse(
	long auctionId,
	String resultType,
	String viewerRole,
	String winnerLabel,
	Long finalPrice,
	String priceDecisionType,
	/** LIVE-06 낙찰 순간 스냅샷. 안 올라왔으면 필드가 통째로 빠진다(NON_NULL) */
	String snapshotUrl,
	String message,
	MyBidSettlementResponse myBidSettlement,
	MyPredictionSettlementResponse myPredictionSettlement,
	List<BidEntryResponse> bids,
	List<PredictionBandResponse> predictionBands,
	Boolean canRelist
) {

	/** 유찰 사유별 안내 문구. 화면이 그대로 띄운다(명세 No-sale 예시의 {@code message}) */
	private static final String NO_BID_MESSAGE = "입찰이 없어 유찰됐어요";
	private static final String HOST_LEFT_MESSAGE = "판매자 이탈로 종료됐어요";
	private static final String FORCED_MESSAGE = "운영 검토로 종료됐어요";

	public static AuctionResultResponse from(AuctionResultQueryResult result) {
		if (result.sold()) {
			return new AuctionResultResponse(
				result.auctionId(),
				result.resultType(),
				result.viewerRole().name(),
				result.winnerLabel(),
				result.finalPrice(),
				// 비크리라 낙찰가는 2등가다. 유효 입찰이 하나뿐이면 하한가로 정해지는데,
				// 그 판정은 집계 엔진 몫이라 지금은 계약 값만 고정해 둔다.
				// TODO(S15P11A704-295): 집계가 붙으면 auction_result.second_highest_bid_amount가
				//  null인 경우를 RESERVE_PRICE로 내린다 — 헤더 조회에 그 컬럼을 추가해야 한다.
				"SECOND_PRICE",
				result.snapshotUrl(),
				null,
				MyBidSettlementResponse.from(result.myBidSettlement()),
				MyPredictionSettlementResponse.from(result.myPredictionSettlement()),
				result.bids().stream().map(BidEntryResponse::from).toList(),
				result.predictionBands().stream().map(PredictionBandResponse::from).toList(),
				null
			);
		}
		return new AuctionResultResponse(
			result.auctionId(),
			result.resultType(),
			result.viewerRole().name(),
			null,
			null,
			null,
			null,
			messageOf(result.resultType()),
			MyBidSettlementResponse.from(result.myBidSettlement()),
			MyPredictionSettlementResponse.from(result.myPredictionSettlement()),
			null,
			null,
			result.canRelist()
		);
	}

	/** 강제 종료만 재등록 CTA를 숨긴다 — 명세 「종료 사유」표 */
	private static String messageOf(String terminationReason) {
		return switch (terminationReason) {
			case "NO_BID" -> NO_BID_MESSAGE;
			case "HOST_LEFT" -> HOST_LEFT_MESSAGE;
			default -> FORCED_MESSAGE;
		};
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record MyBidSettlementResponse(
		String result,
		long submittedAmount,
		Long paymentAmount,
		long releasedAmount
	) {

		static MyBidSettlementResponse from(BidSettlement settlement) {
			if (settlement == null) {
				return null;
			}
			return new MyBidSettlementResponse(
				settlement.result(),
				settlement.submittedAmount(),
				settlement.paymentAmount(),
				settlement.releasedAmount()
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record MyPredictionSettlementResponse(
		long predictionId,
		long predictedPrice,
		long stakeAmount,
		String result,
		String errorBand,
		BigDecimal multiplier,
		Long payoutAmount,
		Long refundAmount
	) {

		static MyPredictionSettlementResponse from(PredictionSettlement settlement) {
			if (settlement == null) {
				return null;
			}
			return new MyPredictionSettlementResponse(
				settlement.predictionId(),
				settlement.predictedPrice(),
				settlement.stakeAmount(),
				settlement.result(),
				settlement.errorBand(),
				settlement.multiplier(),
				settlement.payoutAmount(),
				settlement.refundAmount()
			);
		}
	}

	public record BidEntryResponse(String bidderLabel, long amount, Instant submittedAt) {

		static BidEntryResponse from(MaskedBidEntry entry) {
			return new BidEntryResponse(entry.bidderLabel(), entry.amount(), entry.submittedAt());
		}
	}

	/**
	 * 밴드별 집계. PRED-02 「예측 결과 분포 조회」와 <b>같은 모양</b>이다 —
	 * 두 화면이 다른 형태를 받으면 프론트가 변환을 하나 더 들고 있어야 한다.
	 */
	public record PredictionBandResponse(
		String band,
		long participantCount,
		BigDecimal multiplier,
		long totalPayoutAmount
	) {

		public PredictionBandResponse(
			String band,
			long participantCount,
			int multiplier,
			long totalPayoutAmount
		) {
			this(
				band,
				participantCount,
				BigDecimal.valueOf(multiplier),
				totalPayoutAmount
			);
		}

		static PredictionBandResponse from(
			com.ssafy.bidlive.auction.application.PredictionBandRow row
		) {
			return new PredictionBandResponse(
				row.band(),
				row.participantCount(),
				row.multiplier(),
				row.totalPayoutAmount()
			);
		}
	}
}
