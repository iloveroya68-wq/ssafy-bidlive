package com.ssafy.bidlive.deal.application;

import java.time.Instant;

import com.ssafy.bidlive.deal.domain.DealStatus;

/**
 * DEAL-05 전이 3종의 결과다. 응답 모양이 서로 다르므로(각각 시각 필드가 다르고 수령 확인만 금액을
 * 돌려준다) 레코드를 나누되, 셋이 같은 상태 기계의 결과라 한 파일에 모았다.
 */
public final class DealTransitionResults {

	private DealTransitionResults() {
	}

	/** 배송지 입력 결과. `updatedAt`은 요청을 처리한 시각이다. */
	public record Address(long dealId, DealStatus status, Instant updatedAt) {
	}

	/** 발송 확인 결과. `autoConfirmAt`이 지나면 자동 구매 확정 대상이 된다. */
	public record Shipment(
		long dealId,
		DealStatus status,
		Instant shippedAt,
		Instant autoConfirmAt
	) {
	}

	/** 수령 확인 결과. `sellerPayoutAmount`는 판매자 지갑으로 실제로 나간 에스크로 금액이다. */
	public record Receipt(
		long dealId,
		DealStatus status,
		long sellerPayoutAmount,
		Instant completedAt
	) {
	}
}
