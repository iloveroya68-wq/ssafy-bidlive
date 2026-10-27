package com.ssafy.bidlive.report.api;

import com.ssafy.bidlive.report.api.dto.AdminReportListResponse;
import com.ssafy.bidlive.report.application.AdminReportException;
import com.ssafy.bidlive.report.application.AdminReportQueryService;
import com.ssafy.bidlive.report.domain.AdminReportErrorCode;
import com.ssafy.bidlive.report.domain.ReportedAuctionCursor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-01 운영자 신고 목록.
 *
 * <p>권한은 `SecurityConfig`가 경로 단위로 건다 — 이 레포는 메서드 시큐리티를 켜지 않는다.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

	private final AdminReportQueryService queryService;

	public AdminReportController(AdminReportQueryService queryService) {
		this.queryService = queryService;
	}

	// 파라미터를 전부 `String`으로 받는다. `boolean`·`int`로 바인딩하면 Spring 타입 변환이
	// 먼저 터져 이 봉투를 벗어나고, 코드도 메시지도 없는 빈 400이 나간다.
	@GetMapping
	public AdminReportListResponse getReports(
		@RequestParam(required = false) String includeEnded,
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false) String size
	) {
		return AdminReportListResponse.from(queryService.getReportedAuctions(
			parseIncludeEnded(includeEnded),
			parseCursor(cursor),
			parseSize(size)
		));
	}

	/** `Boolean.parseBoolean`은 아무 문자열이나 `false`로 삼켜 오타를 조용히 통과시킨다. */
	private static boolean parseIncludeEnded(String includeEnded) {
		if (includeEnded == null || includeEnded.isBlank()) {
			return false;
		}
		String value = includeEnded.trim();
		if ("true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}
		throw new AdminReportException(AdminReportErrorCode.INVALID_INCLUDE_ENDED);
	}

	private static ReportedAuctionCursor parseCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			return ReportedAuctionCursor.decode(cursor);
		} catch (IllegalArgumentException exception) {
			throw new AdminReportException(AdminReportErrorCode.INVALID_CURSOR, exception);
		}
	}

	/** 없으면 0을 돌려 서비스의 기본값(20) 분기로 보낸다. */
	private static int parseSize(String size) {
		if (size == null || size.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(size);
		} catch (NumberFormatException exception) {
			throw new AdminReportException(AdminReportErrorCode.INVALID_SIZE, exception);
		}
	}
}
