package com.ssafy.bidlive.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.bidlive.report.domain.AuctionReportErrorCode;
import com.ssafy.bidlive.report.domain.AuctionReportReason;
import org.junit.jupiter.api.Test;

/** 사유 코드 판정과 상세 설명 필수 규칙을 검증한다. 여기서 통과한 값만 DB로 간다. */
class AuctionReportCommandTest {

	@Test
	void acceptsSpecifiedReasonWithoutDescription() {
		AuctionReportCommand command = AuctionReportCommand.of("FALSE_LISTING", null);

		assertThat(command.reason()).isEqualTo(AuctionReportReason.FALSE_LISTING);
		assertThat(command.description()).isNull();
	}

	@Test
	void trimsDescription() {
		assertThat(AuctionReportCommand.of("PROHIBITED_ITEM", "  주류입니다  ").description())
			.isEqualTo("주류입니다");
	}

	/** 화면이 빈 문자열을 보내도 "안 적음"으로 눕힌다 — 상세 설명 칸은 선택이다. */
	@Test
	void treatsBlankDescriptionAsAbsent() {
		assertThat(AuctionReportCommand.of("FALSE_LISTING", "   ").description()).isNull();
	}

	@Test
	void requiresDescriptionForOther() {
		assertThatThrownBy(() -> AuctionReportCommand.of("OTHER", "  "))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.INVALID_REPORT_REQUEST));

		assertThat(AuctionReportCommand.of("OTHER", "다른 문제가 있습니다").reason())
			.isEqualTo(AuctionReportReason.OTHER);
	}

	/** 모르는 사유 코드는 400이다. enum 바인딩에 맡기면 500이 된다. */
	@Test
	void rejectsUnknownReasonCode() {
		assertThatThrownBy(() -> AuctionReportCommand.of("ITEM_NOT_AS_DESCRIBED", "설명"))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.INVALID_REPORT_REQUEST));
	}

	@Test
	void rejectsMissingReasonCode() {
		assertThatThrownBy(() -> AuctionReportCommand.of(null, "설명"))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.INVALID_REPORT_REQUEST));
	}

	@Test
	void rejectsTooLongDescription() {
		assertThatThrownBy(() -> AuctionReportCommand.of("OTHER", "가".repeat(1_001)))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.INVALID_REPORT_REQUEST));
	}

	private static AuctionReportErrorCode codeOf(Throwable throwable) {
		assertThat(throwable).isInstanceOf(AuctionReportException.class);
		return ((AuctionReportException) throwable).getErrorCode();
	}
}
