package com.ssafy.bidlive.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ssafy.bidlive.report.domain.AuctionReportErrorCode;
import com.ssafy.bidlive.report.domain.AuctionReportReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 신고 판정 순서와 거절 사유별 오류 코드를 검증한다. */
class AuctionReportServiceTest {

	private static final long AUCTION_ID = 100L;
	private static final long SELLER_ID = 25L;
	private static final long REPORTER_ID = 42L;
	private static final Instant NOW = Instant.parse("2026-07-22T06:00:00Z");

	private AuctionReportPersistence persistence;
	private AuctionReportService service;

	@BeforeEach
	void setUp() {
		persistence = mock(AuctionReportPersistence.class);
		service = new AuctionReportService(persistence, Clock.fixed(NOW, ZoneOffset.UTC));
		when(persistence.save(anyLong(), anyLong(), any(), any()))
			.thenReturn(Optional.of(new AuctionReportResult(123L, "RECEIVED", NOW)));
	}

	@Test
	void acceptsReportOnLiveAuction() {
		givenTarget("LIVE", false);

		AuctionReportResult result = service.report(AUCTION_ID, REPORTER_ID, command());

		assertThat(result.reportId()).isEqualTo(123L);
		assertThat(result.status()).isEqualTo("RECEIVED");
		verify(persistence).save(AUCTION_ID, REPORTER_ID, command(), NOW);
	}

	@Test
	void acceptsReportOnWaitingAuction() {
		givenTarget("WAITING", false);

		service.report(AUCTION_ID, REPORTER_ID, command());

		verify(persistence).save(AUCTION_ID, REPORTER_ID, command(), NOW);
	}

	@Test
	void rejectsUnauthenticatedRequest() {
		assertThatThrownBy(() -> service.report(AUCTION_ID, null, command()))
			.satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuctionReportErrorCode.UNAUTHORIZED));
		verify(persistence, never()).findReportTarget(anyLong());
	}

	@Test
	void rejectsMissingBody() {
		assertThatThrownBy(() -> service.report(AUCTION_ID, REPORTER_ID, null))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.INVALID_REPORT_REQUEST));
	}

	@Test
	void rejectsMissingAuction() {
		when(persistence.findReportTarget(AUCTION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.report(AUCTION_ID, REPORTER_ID, command()))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.AUCTION_NOT_FOUND));
	}

	/** 삭제된 방은 판매자 검사보다 먼저 404다 — 지워진 방의 판매자 정보를 흘리지 않는다. */
	@Test
	void rejectsDeletedAuctionAsNotFound() {
		givenTarget("WAITING", true);

		assertThatThrownBy(() -> service.report(AUCTION_ID, REPORTER_ID, command()))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.AUCTION_NOT_FOUND));
	}

	@Test
	void rejectsSellerReportingOwnAuction() {
		givenTarget("LIVE", false);

		assertThatThrownBy(() -> service.report(AUCTION_ID, SELLER_ID, command()))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.OWN_AUCTION_REPORT_NOT_ALLOWED));
		verify(persistence, never()).save(anyLong(), anyLong(), any(), any());
	}

	/** 본인 경매 판정이 상태 판정보다 앞선다 — 종료된 자기 방을 신고해도 403이다. */
	@Test
	void checksOwnAuctionBeforeStatus() {
		givenTarget("ENDED", false);

		assertThatThrownBy(() -> service.report(AUCTION_ID, SELLER_ID, command()))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.OWN_AUCTION_REPORT_NOT_ALLOWED));
	}

	@Test
	void rejectsEndedAuction() {
		givenTarget("ENDED", false);

		assertThatThrownBy(() -> service.report(AUCTION_ID, REPORTER_ID, command()))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.AUCTION_NOT_REPORTABLE));
	}

	/** 알 수 없는 상태 문자열도 신고 불가로 막는다 — enum 변환 실패로 500을 내지 않는다. */
	@Test
	void rejectsUnknownStatusString() {
		givenTarget("SOMETHING_ELSE", false);

		assertThatThrownBy(() -> service.report(AUCTION_ID, REPORTER_ID, command()))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.AUCTION_NOT_REPORTABLE));
	}

	@Test
	void rejectsDuplicateReportFromSameMember() {
		givenTarget("LIVE", false);
		when(persistence.existsReport(AUCTION_ID, REPORTER_ID)).thenReturn(true);

		assertThatThrownBy(() -> service.report(AUCTION_ID, REPORTER_ID, command()))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.DUPLICATE_AUCTION_REPORT));
		verify(persistence, never()).save(anyLong(), anyLong(), any(), any());
	}

	/**
	 * 앱 EXISTS를 통과한 동시 요청은 유니크 인덱스(V16)가 잡는다. 저장이 빈 값을 주면 중복이다 —
	 * `@Transactional`만으로는 막히지 않는 구간이라 DB 보장을 그대로 오류로 옮긴다.
	 */
	@Test
	void mapsUniqueIndexRejectionToDuplicate() {
		givenTarget("LIVE", false);
		when(persistence.save(anyLong(), anyLong(), any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.report(AUCTION_ID, REPORTER_ID, command()))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.DUPLICATE_AUCTION_REPORT));
	}

	/**
	 * 비공개 방은 판매자 외에 존재를 숨긴다 — 상세·입장·수정·삭제·시작과 같은 규칙이다.
	 * 201을 주면 응답만으로 "그 id의 방이 있고 진행 중이다"가 드러난다.
	 */
	@Test
	void hidesPrivateAuctionFromNonSellerAsNotFound() {
		givenTarget("LIVE", "PRIVATE", false);

		assertThatThrownBy(() -> service.report(AUCTION_ID, REPORTER_ID, command()))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.AUCTION_NOT_FOUND));
		verify(persistence, never()).save(anyLong(), anyLong(), any(), any());
	}

	/** 알 수 없는 visibility 값도 비공개로 취급한다 — 값 하나 때문에 노출되지 않는다. */
	@Test
	void treatsUnknownVisibilityAsPrivate() {
		givenTarget("LIVE", "SOMETHING_ELSE", false);

		assertThatThrownBy(() -> service.report(AUCTION_ID, REPORTER_ID, command()))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.AUCTION_NOT_FOUND));
	}

	/** 비공개 방을 판매자가 신고하면 숨김보다 본인 경매 판정이 앞선다 — 그쪽이 더 정확한 사유다. */
	@Test
	void tellsSellerOwnAuctionEvenWhenPrivate() {
		givenTarget("LIVE", "PRIVATE", false);

		assertThatThrownBy(() -> service.report(AUCTION_ID, SELLER_ID, command()))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionReportErrorCode.OWN_AUCTION_REPORT_NOT_ALLOWED));
	}

	private void givenTarget(String status, boolean deleted) {
		givenTarget(status, "PUBLIC", deleted);
	}

	private void givenTarget(String status, String visibility, boolean deleted) {
		when(persistence.findReportTarget(AUCTION_ID)).thenReturn(Optional.of(
			new AuctionReportTarget(AUCTION_ID, SELLER_ID, status, visibility, deleted)
		));
	}

	private static AuctionReportCommand command() {
		return new AuctionReportCommand(
			AuctionReportReason.INAPPROPRIATE_BROADCAST,
			"판매자가 방송 중 부적절한 발언을 반복했습니다."
		);
	}

	private static AuctionReportErrorCode codeOf(Throwable throwable) {
		assertThat(throwable).isInstanceOf(AuctionReportException.class);
		return ((AuctionReportException) throwable).getErrorCode();
	}
}
