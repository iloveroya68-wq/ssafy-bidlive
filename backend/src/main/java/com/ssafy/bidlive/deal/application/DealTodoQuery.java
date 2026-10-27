package com.ssafy.bidlive.deal.application;

import java.util.List;

/** 「지금 할 일」 조회 계약이다. */
public interface DealTodoQuery {

	/**
	 * 이 회원이 **직접 처리해야 하는** 거래만 오래된 순으로 돌려준다.
	 *
	 * <p>액션 주체 판정을 쿼리에 두는 이유: 상태·역할 조합 세 가지만 할 일이라 WHERE로 걸러내는 편이
	 * 전부 읽어 와 자바에서 버리는 것보다 싸고, 정렬(`updatedAt` 오름차순)도 같은 자리에서 끝난다.
	 */
	List<DealTodoItem> findActionableDeals(long memberId);
}
