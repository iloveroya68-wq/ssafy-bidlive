package com.ssafy.bidlive.deal.api.dto;

import java.util.List;

import com.ssafy.bidlive.deal.application.DealTodoItem;

/**
 * 「지금 할 일」 목록 응답이다.
 *
 * <p>{@code totalCount}는 {@code items.size()}와 항상 같다 — 페이지네이션이 없는 API라서다. 그래도
 * 명세가 별도 필드로 두었고 프론트가 탭 배지에 쓰기 때문에 그대로 내려준다.
 */
public record DealTodosResponse(
	int totalCount,
	List<DealTodoResponse> items
) {

	public static DealTodosResponse from(List<DealTodoItem> todos) {
		return new DealTodosResponse(
			todos.size(),
			todos.stream().map(DealTodoResponse::from).toList()
		);
	}
}
