package com.ssafy.bidlive.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.ssafy.bidlive.report.domain.ReportedAuctionCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** native 결과 행이 도메인 record로 옳게 변환되는지, 정렬·필터가 SQL에 실제로 들어 있는지 본다. */
class JpaAdminReportQueryAdapterTest {

	private static final Instant AT = Instant.parse("2026-07-31T10:31:00Z");

	private EntityManager entityManager;
	private Query query;
	private JpaAdminReportQueryAdapter adapter;

	@BeforeEach
	void setUp() {
		entityManager = mock(EntityManager.class);
		query = mock(Query.class);
		when(entityManager.createNativeQuery(anyString())).thenReturn(query);
		when(query.setParameter(anyString(), any())).thenReturn(query);
		adapter = new JpaAdminReportQueryAdapter(entityManager);
	}

	@Test
	void mapsReportedAuctionRow() {
		// 배열 하나를 List.of에 넘기면 varargs로 펼쳐져 원소 8개짜리 리스트가 된다 — singletonList로 감싼다.
		when(query.getResultList()).thenReturn(Collections.singletonList(new Object[]{
			9203L, "빈티지 카메라", "https://img", "서연", "LIVE", 7L, Timestamp.from(AT)
		}));

		var items = adapter.findReportedAuctions(false, null, 21);

		assertThat(items).hasSize(1);
		assertThat(items.getFirst().auctionId()).isEqualTo(9203L);
		assertThat(items.getFirst().title()).isEqualTo("빈티지 카메라");
		assertThat(items.getFirst().thumbnailUrl()).isEqualTo("https://img");
		assertThat(items.getFirst().sellerNickname()).isEqualTo("서연");
		assertThat(items.getFirst().status()).isEqualTo("LIVE");
		assertThat(items.getFirst().reportCount()).isEqualTo(7);
		assertThat(items.getFirst().latestReportedAt()).isEqualTo(AT);
		// 사유는 두 번째 쿼리 소관이라 여기서는 비어 있다.
		assertThat(items.getFirst().reasonCounts()).isEmpty();
	}

	@Test
	void excludesEndedAndDeletedRoomsByDefault() {
		when(query.getResultList()).thenReturn(List.of());

		adapter.findReportedAuctions(false, null, 21);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(entityManager).createNativeQuery(sql.capture());
		assertThat(sql.getValue())
			.contains("ar.deleted_at IS NULL")
			// 블랙리스트(`<> 'ENDED'`)로 되돌리면 계약에 없는 네 번째 상태가 목록에 샌다
			.contains("ar.status IN ('WAITING', 'LIVE')")
			.contains("(:includeEnded = TRUE AND ar.status = 'ENDED')")
			.contains("ORDER BY MAX(r.created_at) DESC, ar.auction_room_id DESC");
		verify(query).setParameter("includeEnded", false);
	}

	/** 커서 두 값을 바꿔 실으면 2페이지가 통째로 비거나 같은 페이지가 반복된다. */
	@Test
	void bindsCursorValuesToMatchingParameters() {
		when(query.getResultList()).thenReturn(List.of());

		adapter.findReportedAuctions(false, new ReportedAuctionCursor(AT, 9203L), 21);

		verify(query).setParameter("cursorAt", AT.toString());
		verify(query).setParameter("cursorAuctionId", 9203L);
		verify(query).setParameter("limit", 21);
	}

	@Test
	void ordersReasonCountsByCountThenCode() {
		// 동수일 때 순서가 흔들리면 화면의 「금지 품목 5 · 허위 3」이 새로고침마다 뒤바뀐다.
		when(query.getResultList()).thenReturn(List.of());

		adapter.findReasonCounts(List.of(9203L));

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(entityManager).createNativeQuery(sql.capture());
		assertThat(sql.getValue()).contains("ORDER BY r.auction_room_id, COUNT(*) DESC, r.reason_code ASC");
	}

	@Test
	void groupsReasonCountsByAuction() {
		when(query.getResultList()).thenReturn(List.of(
			new Object[]{9203L, "PROHIBITED_ITEM", 5L},
			new Object[]{9203L, "FALSE_LISTING", 3L},
			new Object[]{9204L, "OTHER", 1L}
		));

		var grouped = adapter.findReasonCounts(List.of(9203L, 9204L));

		assertThat(grouped.get(9203L)).extracting("reasonCode", "count")
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("PROHIBITED_ITEM", 5),
				org.assertj.core.groups.Tuple.tuple("FALSE_LISTING", 3)
			);
		assertThat(grouped.get(9204L)).hasSize(1);
	}

	@Test
	void returnsEmptyMapWithoutQueryingWhenNoAuctionIds() {
		assertThat(adapter.findReasonCounts(List.of())).isEmpty();
		verify(entityManager, org.mockito.Mockito.never()).createNativeQuery(anyString());
	}
}
