package com.ssafy.bidlive.deal.api;

import com.ssafy.bidlive.deal.api.dto.DealTodosResponse;
import com.ssafy.bidlive.deal.application.DealTodoService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 마이페이지 「지금 할 일」 — 로그인 사용자가 직접 처리해야 하는 거래만 내려준다(MEM-02). */
@RestController
@RequestMapping("/api/v1/members/me/deals")
public class DealTodoController {

	private final DealTodoService todoService;

	public DealTodoController(DealTodoService todoService) {
		this.todoService = todoService;
	}

	@GetMapping("/todos")
	public DealTodosResponse findMyTodos(Authentication authentication) {
		return DealTodosResponse.from(
			todoService.findMyTodos(DealPrincipal.memberId(authentication))
		);
	}
}
