package com.ssafy.bidlive.report.api;

import com.ssafy.bidlive.report.api.dto.AuctionReportRequest;
import com.ssafy.bidlive.report.api.dto.AuctionReportResponse;
import com.ssafy.bidlive.report.application.AuctionReportException;
import com.ssafy.bidlive.report.application.AuctionReportService;
import com.ssafy.bidlive.report.domain.AuctionReportErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 대기방·라이브 경매를 신고한다(DEAL-04). 접수만 하고 강제 종료는 운영자 검토 몫이다. */
@RestController
@RequestMapping("/api/v1/auctions")
public class AuctionReportController {

	private final AuctionReportService reportService;

	public AuctionReportController(AuctionReportService reportService) {
		this.reportService = reportService;
	}

	@PostMapping("/{auctionId}/reports")
	@ResponseStatus(HttpStatus.CREATED)
	public AuctionReportResponse report(
		@PathVariable long auctionId,
		@RequestBody(required = false) AuctionReportRequest request,
		Authentication authentication
	) {
		return AuctionReportResponse.from(reportService.report(
			auctionId,
			authenticatedMemberId(authentication),
			request == null ? null : request.toCommand()
		));
	}

	/**
	 * 익명 요청은 `AnonymousAuthenticationToken`으로 들어와 {@code isAuthenticated()}가 참이다.
	 * 이름이 숫자가 아니라는 점으로 걸러야 하고, 안 걸르면 {@code Long.parseLong}이 터져 500이 된다.
	 */
	private static Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new AuctionReportException(AuctionReportErrorCode.UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new AuctionReportException(AuctionReportErrorCode.UNAUTHORIZED);
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new AuctionReportException(AuctionReportErrorCode.UNAUTHORIZED);
		}
	}
}
