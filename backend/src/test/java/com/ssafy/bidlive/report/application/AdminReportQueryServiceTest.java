package com.ssafy.bidlive.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.ssafy.bidlive.report.domain.ReportReasonCount;
import com.ssafy.bidlive.report.domain.ReportedAuctionCursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 페이지 경계와 사유 조립을 검증한다.
 *
 * <p>다음 장 판정은 size + 1건을 읽어서 한다 — COUNT를 따로 치면 두 쿼리가 다른 스냅샷을 보게
 * 되어 「다음 장이 있다는데 비어 있는」 경계가 생긴다.
 */
@ExtendWith(MockitoExtension.class)
class AdminReportQueryServiceTest {

	private static final Instant AT = Instant.parse("2026-07-31T10:31:00Z");

	@Mock
	private AdminReportQueryPersistence persistence;

	private AdminReportQueryService service;

	@BeforeEach
	void setUp() {
		service = new AdminReportQueryService(persistence);
	}

	@Test
	void readsOneMoreThanSizeToDetectNextPage() {
		when(persistence.findReportedAuctions(anyBoolean(), any(), eq(3)))
			.thenReturn(auctions(2));
		when(persistence.findReasonCounts(any())).thenReturn(Map.of());

		ReportedAuctionPage page = service.getReportedAuctions(false, null, 2);

		assertThat(page.items()).hasSize(2);
		assertThat(page.hasNext()).isFalse();
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void trimsExtraRowAndEmitsCursorWhenMoreExist() {
		when(persistence.findReportedAuctions(anyBoolean(), any(), eq(3)))
			.thenReturn(auctions(3));
		when(persistence.findReasonCounts(any())).thenReturn(Map.of());

		ReportedAuctionPage page = service.getReportedAuctions(false, null, 2);

		assertThat(page.items()).hasSize(2);
		assertThat(page.hasNext()).isTrue();
		// 커서는 **잘라낸 뒤 마지막 행** 기준이어야 한다. 여분 행으로 만들면 한 건이 건너뛰어진다.
		assertThat(ReportedAuctionCursor.decode(page.nextCursor()).auctionId())
			.isEqualTo(page.items().getLast().auctionId());
	}

	@Test
	void attachesReasonCountsToMatchingAuction() {
		when(persistence.findReportedAuctions(anyBoolean(), any(), eq(21)))
			.thenReturn(auctions(1));
		when(persistence.findReasonCounts(List.of(1L))).thenReturn(Map.of(
			1L, List.of(new ReportReasonCount("PROHIBITED_ITEM", 5))
		));

		ReportedAuctionPage page = service.getReportedAuctions(false, null, 0);

		assertThat(page.items().getFirst().reasonCounts())
			.extracting("reasonCode").containsExactly("PROHIBITED_ITEM");
	}

	@Test
	void leavesReasonCountsEmptyWhenLookupMisses() {
		when(persistence.findReportedAuctions(anyBoolean(), any(), eq(21)))
			.thenReturn(auctions(1));
		when(persistence.findReasonCounts(List.of(1L))).thenReturn(Map.of());

		ReportedAuctionPage page = service.getReportedAuctions(false, null, 0);

		assertThat(page.items().getFirst().reasonCounts()).isEmpty();
	}

	@Test
	void clampsSizeToMaximum() {
		when(persistence.findReportedAuctions(anyBoolean(), any(), eq(101)))
			.thenReturn(List.of());
		when(persistence.findReasonCounts(any())).thenReturn(Map.of());

		service.getReportedAuctions(false, null, 500);

		verify(persistence).findReportedAuctions(false, null, 101);
	}

	private static List<ReportedAuction> auctions(int count) {
		return IntStream.rangeClosed(1, count)
			.mapToObj(index -> new ReportedAuction(
				index, "방 " + index, null, "서연", "LIVE", 3, AT, List.of()
			))
			.toList();
	}
}
