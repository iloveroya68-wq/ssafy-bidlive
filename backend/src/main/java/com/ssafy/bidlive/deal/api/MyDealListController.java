package com.ssafy.bidlive.deal.api;

import com.ssafy.bidlive.deal.api.dto.MyDealListResponse;
import com.ssafy.bidlive.deal.application.DealListCriteria;
import com.ssafy.bidlive.deal.application.DealException;
import com.ssafy.bidlive.deal.application.DealListQueryService;
import com.ssafy.bidlive.deal.domain.DealErrorCode;
import com.ssafy.bidlive.deal.domain.DealListCursor;
import com.ssafy.bidlive.deal.domain.DealRole;
import com.ssafy.bidlive.deal.domain.DealStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEAL-01 내 거래 목록 조회.
 *
 * <p>「지금 할 일」({@code DealTodoController})과 같은 기저 경로를 쓰지만 다른 계약이다 —
 * 저쪽은 <b>지금 내가 처리해야 하는 것</b>만, 이쪽은 <b>참여한 거래 전체</b>를 커서로 준다.
 * 경로가 한 겹 차이(`/deals` vs `/deals/todos`)라 헷갈리기 쉽다.
 */
@RestController
@RequestMapping("/api/v1/members/me/deals")
public class MyDealListController {

	private final DealListQueryService dealListQueryService;

	public MyDealListController(DealListQueryService dealListQueryService) {
		this.dealListQueryService = dealListQueryService;
	}

	@GetMapping
	public MyDealListResponse getDeals(
		@RequestParam(required = false) String role,
		@RequestParam(required = false) String status,
		@RequestParam(required = false) String cursor,
		// int로 받으면 `size=abc`가 Spring 타입 변환에서 터져 이 도메인 오류 봉투를 벗어난다.
		// 문자열로 받아 다른 파라미터와 같은 경로로 400을 낸다.
		@RequestParam(required = false) String size,
		Authentication authentication
	) {
		return MyDealListResponse.from(
			dealListQueryService.getDeals(new DealListCriteria(
				DealPrincipal.memberId(authentication),
				parseRole(role),
				parseStatus(status),
				parseCursor(cursor),
				parseSize(size)
			))
		);
	}

	/** 알아볼 수 없는 값은 400이다 — 조용히 무시하면 필터를 건 사용자가 전체 목록을 보게 된다 */
	private static DealRole parseRole(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return DealRole.valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw new DealException(DealErrorCode.INVALID_DEAL_FILTER);
		}
	}

	/**
	 * {@code DealStatus}로 <b>검증만</b> 하고 값은 문자열 그대로 넘긴다 —
	 * {@code deal_status}에 DB CHECK가 없어 조회 결과에는 enum에 없는 값도 올 수 있고,
	 * 그때 enum으로 왕복시키면 목록 전체가 500이 된다.
	 */
	private static String parseStatus(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return DealStatus.valueOf(value).name();
		} catch (IllegalArgumentException exception) {
			throw new DealException(DealErrorCode.INVALID_DEAL_FILTER);
		}
	}

	private static DealListCursor parseCursor(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return DealListCursor.decode(value);
		} catch (IllegalArgumentException exception) {
			throw new DealException(DealErrorCode.INVALID_CURSOR);
		}
	}

	/** 생략하면 0(서비스가 기본값으로 되돌린다). 숫자가 아니면 필터와 같은 400이다 */
	private static int parseSize(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			throw new DealException(DealErrorCode.INVALID_DEAL_FILTER);
		}
	}
}
