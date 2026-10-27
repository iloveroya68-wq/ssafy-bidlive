package com.ssafy.bidlive.deal.application;

import java.util.List;

import com.ssafy.bidlive.deal.domain.DealErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지 「지금 할 일」 목록을 조회한다(MEM-02). */
@Service
public class DealTodoService {

	private final DealTodoQuery todoQuery;

	public DealTodoService(DealTodoQuery todoQuery) {
		this.todoQuery = todoQuery;
	}

	/** 할 일이 없으면 빈 목록이다 — 명세가 `totalCount` 0과 빈 배열을 요구한다. */
	@Transactional(readOnly = true)
	public List<DealTodoItem> findMyTodos(Long memberId) {
		if (memberId == null) {
			throw new DealException(DealErrorCode.UNAUTHORIZED);
		}
		return todoQuery.findActionableDeals(memberId);
	}
}
