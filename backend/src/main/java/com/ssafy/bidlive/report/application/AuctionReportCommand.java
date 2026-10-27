package com.ssafy.bidlive.report.application;

import com.ssafy.bidlive.report.domain.AuctionReportErrorCode;
import com.ssafy.bidlive.report.domain.AuctionReportReason;

/**
 * 접수할 신고 내용이다. 사유 문자열을 enum으로 좁히고 설명 길이를 확인한 값만 저장으로 넘긴다.
 *
 * <p>명세의 {@code evidenceUrls}는 받지 않는다. `report` 테이블에 증빙 컬럼이 없고 와이어프레임
 * 신고 모달에도 업로드 칸이 없다 — 컬럼과 함께 넣는 편이 맞아서 별도 이슈로 끊었다. 지금 보내면
 * Jackson이 조용히 버린다.
 */
public record AuctionReportCommand(
	AuctionReportReason reason,
	String description
) {

	/** `report.description`은 TEXT지만 운영자가 읽는 글이라 상한을 둔다. */
	private static final int DESCRIPTION_MAX = 1_000;

	/**
	 * 원문 요청을 검증된 명령으로 옮긴다.
	 *
	 * <p>{@code OTHER}만 설명이 필수다(명세: *"`OTHER`인 경우 `description`은 필수"*). 나머지 사유는
	 * 선택이며 빈 문자열은 `null`로 눕혀 "안 적음"과 구분되지 않게 한다.
	 */
	public static AuctionReportCommand of(String reasonCode, String description) {
		AuctionReportReason reason = AuctionReportReason.parse(reasonCode);
		if (reason == null) {
			throw new AuctionReportException(AuctionReportErrorCode.INVALID_REPORT_REQUEST);
		}

		String trimmed = description == null || description.isBlank()
			? null
			: description.trim();
		if (trimmed != null && trimmed.length() > DESCRIPTION_MAX) {
			throw new AuctionReportException(AuctionReportErrorCode.INVALID_REPORT_REQUEST);
		}
		if (reason.requiresDescription() && trimmed == null) {
			throw new AuctionReportException(AuctionReportErrorCode.INVALID_REPORT_REQUEST);
		}
		return new AuctionReportCommand(reason, trimmed);
	}
}
