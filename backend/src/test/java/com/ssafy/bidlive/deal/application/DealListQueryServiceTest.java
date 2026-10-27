package com.ssafy.bidlive.deal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import com.ssafy.bidlive.deal.domain.DealListCursor;
import com.ssafy.bidlive.deal.domain.DealRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 커서 페이지 경계를 잠근다. 여기서 틀리면 <b>화면은 멀쩡하고 목록만 조용히 어긋난다</b> —
 * 마지막 줄이 사라지거나, 다음 장이 있다는데 눌러 보면 비어 있다.
 */
class DealListQueryServiceTest {

	private static final long MEMBER_ID = 25L;
	private static final Instant BASE = Instant.parse("2026-07-20T13:00:00Z");

	private DealListQuery query;
	private DealListQueryService service;

	@BeforeEach
	void setUp() {
		query = mock(DealListQuery.class);
		service = new DealListQueryService(query);
	}

	private static DealListItem item(long dealId) {
		return new DealListItem(
			dealId,
			100L + dealId,
			DealRole.BUYER,
			"키크론 Q1 Pro",
			null,
			74_000L,
			"COMPLETED",
			"판**",
			BASE,
			BASE.minusSeconds(dealId)
		);
	}

	private static List<DealListItem> items(int count) {
		return IntStream.rangeClosed(1, count).mapToObj(i -> item(i)).toList();
	}

	private DealListCriteria criteria(int size) {
		return new DealListCriteria(MEMBER_ID, null, null, null, size);
	}

	@Test
	void 정규화된_size를_어댑터에_그대로_넘긴다() {
		// 실제 +1은 어댑터(criteria.size() + 1)가 한다 — 여기서 검사하는 것은 넘어가는 값이다
		when(query.findDeals(any())).thenReturn(items(3));

		service.getDeals(criteria(20));

		ArgumentCaptor<DealListCriteria> captor = ArgumentCaptor.forClass(DealListCriteria.class);
		verify(query).findDeals(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(20);
	}

	@Test
	void 한_건_더_왔으면_잘라내고_hasNext를_세운다() {
		when(query.findDeals(any())).thenReturn(items(4));

		DealListPage page = service.getDeals(criteria(3));

		assertThat(page.items()).hasSize(3);
		assertThat(page.hasNext()).isTrue();
		assertThat(page.nextCursor()).isNotNull();
	}

	@Test
	void 딱_맞게_왔으면_마지막_장이다() {
		when(query.findDeals(any())).thenReturn(items(3));

		DealListPage page = service.getDeals(criteria(3));

		assertThat(page.items()).hasSize(3);
		assertThat(page.hasNext()).isFalse();
		// 다음 장이 없으면 커서를 주지 않는다 — 주면 화면이 빈 페이지를 한 번 더 부른다
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void 다음_커서는_잘라낸_뒤의_마지막_행_기준이다() {
		// size + 1번째 행 기준으로 만들면 그 한 건이 다음 장에서 통째로 빠진다
		when(query.findDeals(any())).thenReturn(items(4));

		DealListPage page = service.getDeals(criteria(3));

		DealListItem lastShown = page.items().get(2);
		assertThat(page.nextCursor())
			.isEqualTo(new DealListCursor(lastShown.sortedAt(), lastShown.dealId()).encode());
	}

	@Test
	void size는_기본_20_최대_100으로_좁힌다() {
		when(query.findDeals(any())).thenReturn(List.of());
		ArgumentCaptor<DealListCriteria> captor = ArgumentCaptor.forClass(DealListCriteria.class);

		service.getDeals(criteria(0));
		service.getDeals(criteria(500));
		service.getDeals(criteria(-1));

		verify(query, org.mockito.Mockito.times(3)).findDeals(captor.capture());
		assertThat(captor.getAllValues()).extracting(DealListCriteria::size)
			.containsExactly(20, 100, 20);
	}

	@Test
	void enum에_없는_상태가_와도_그대로_실어_보낸다() {
		// deal_status에는 DB CHECK가 없다. valueOf로 왕복시키면 그 한 행 때문에 목록 전체가 500이 된다
		DealListItem unknown = new DealListItem(
			9L, 109L, DealRole.SELLER, "상품", null, 1_000L, "SOMETHING_NEW", "판**", null, BASE
		);
		when(query.findDeals(any())).thenReturn(List.of(unknown));

		DealListPage page = service.getDeals(criteria(20));

		assertThat(page.items()).singleElement()
			.extracting(DealListItem::status).isEqualTo("SOMETHING_NEW");
	}

	@Test
	void 커서는_왕복한다() {
		DealListCursor cursor = new DealListCursor(BASE, 42L);

		assertThat(DealListCursor.decode(cursor.encode())).isEqualTo(cursor);
	}
}
