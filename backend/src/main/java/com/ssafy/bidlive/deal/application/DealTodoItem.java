package com.ssafy.bidlive.deal.application;

import java.time.Instant;

import com.ssafy.bidlive.deal.domain.DealRole;
import com.ssafy.bidlive.deal.domain.DealStatus;
import com.ssafy.bidlive.deal.domain.DealTodoAction;

/**
 * 「지금 할 일」 한 건이다. 별도 할 일 테이블 없이 거래 상태와 내 역할로 계산한다.
 *
 * <p>{@code updatedAt}은 `deal_shipping.updated_at`(트리거가 갱신)이고, 배송지 입력 전에는 그 행이
 * 없어 `deal.created_at`으로 대체된다 — `deal`에는 갱신 시각 컬럼이 없다.
 */
public record DealTodoItem(
	long dealId,
	long auctionId,
	String productTitle,
	String imageUrl,
	DealRole role,
	DealStatus status,
	long finalPrice,
	Instant endedAt,
	Instant updatedAt,
	Instant autoConfirmAt
) {

	/** 상태와 역할에서 결정되는 액션. 문구·이동 경로도 여기서 나온다. */
	public DealTodoAction action() {
		return DealTodoAction.forStatusAndRole(status, role);
	}
}
