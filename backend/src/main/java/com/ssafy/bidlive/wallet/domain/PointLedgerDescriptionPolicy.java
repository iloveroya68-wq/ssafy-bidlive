package com.ssafy.bidlive.wallet.domain;

/** 거래 유형과 원인 도메인을 화면 표시용 원장 설명으로 변환한다. */
public final class PointLedgerDescriptionPolicy {

	private PointLedgerDescriptionPolicy() {
	}

	public static String describe(
		PointLedgerTransactionType transactionType,
		PointLedgerReferenceType referenceType
	) {
		return switch (transactionType) {
			case CHARGE -> "거래 포인트 충전";
			case HOLD -> "경매 입찰 포인트 보류";
			case RELEASE -> "경매 입찰 포인트 보류 해제";
			case SETTLEMENT -> settlementDescription(referenceType);
			case REFUND -> refundDescription(referenceType);
			case GRANT -> "베팅 포인트 보상 지급";
			case BET -> "낙찰가 예측 베팅";
			case PAYOUT -> "낙찰가 예측 배당";
		};
	}

	private static String settlementDescription(
		PointLedgerReferenceType referenceType
	) {
		return switch (referenceType) {
			case DEAL -> "거래 정산 포인트 지급";
			case AUCTION -> "경매 낙찰 포인트 정산";
			default -> "포인트 정산";
		};
	}

	private static String refundDescription(
		PointLedgerReferenceType referenceType
	) {
		return switch (referenceType) {
			case PREDICTION -> "예측 베팅 포인트 환불";
			case PAYMENT -> "거래 포인트 환불";
			case DEAL -> "거래 취소 포인트 환불";
			case AUCTION -> "경매 종료 포인트 환불";
			default -> "포인트 환불";
		};
	}
}
