package com.ssafy.bidlive.prediction.application;

/** 경매 결과에 따라 정상 배당 또는 무효 종료 환불 정산을 요청한다. */
public record PredictionSettlementCommand(
	Long auctionId,
	Long auctionResultId,
	Long finalPrice,
	CompletionType completionType
) {

	public PredictionSettlementCommand {
		requirePositive(auctionId, "auctionId");
		requirePositive(auctionResultId, "auctionResultId");
		if (completionType == null) {
			throw new IllegalArgumentException(
				"completionType must not be null"
			);
		}
		if (completionType == CompletionType.SOLD) {
			requirePositive(finalPrice, "finalPrice");
		} else if (finalPrice != null) {
			throw new IllegalArgumentException(
				"refunded settlement must not have finalPrice"
			);
		}
	}

	public boolean refunded() {
		return completionType != CompletionType.SOLD;
	}

	private static void requirePositive(Long value, String name) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}

	public enum CompletionType {
		SOLD,
		NO_BID,
		HOST_LEFT,
		FORCED
	}
}
