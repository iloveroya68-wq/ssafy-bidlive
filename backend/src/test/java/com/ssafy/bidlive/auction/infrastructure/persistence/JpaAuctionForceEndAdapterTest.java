package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionForceEndPersistence.ResultCounts;
import com.ssafy.bidlive.auction.domain.AuctionTerminationReason;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 강제 종료 native persistence의 행 변환과 결과 행 재사용 분기를 검증한다. */
class JpaAuctionForceEndAdapterTest {

	private EntityManager entityManager;
	private Query query;
	private JpaAuctionForceEndAdapter adapter;

	@BeforeEach
	void setUp() {
		entityManager = mock(EntityManager.class);
		query = mock(Query.class);
		when(entityManager.createNativeQuery(anyString())).thenReturn(query);
		when(query.setParameter(anyString(), any())).thenReturn(query);
		adapter = new JpaAuctionForceEndAdapter(entityManager);
	}

	@Test
	void mapsForceEndTargetProjection() {
		// 배열 하나를 `List.of`에 넘기면 varargs로 펼쳐져 원소 3개짜리 리스트가 된다.
		when(query.getResultList()).thenReturn(Collections.singletonList(
			new Object[]{42L, 7L, "LIVE", Boolean.FALSE}
		));

		assertThat(adapter.findTarget(42L)).hasValueSatisfying(target -> {
			assertThat(target.auctionId()).isEqualTo(42L);
			assertThat(target.sellerMemberId()).isEqualTo(7L);
			assertThat(target.status()).isEqualTo("LIVE");
			assertThat(target.deleted()).isFalse();
		});
	}

	@Test
	void returnsEmptyWhenAuctionMissing() {
		when(query.getResultList()).thenReturn(List.of());

		assertThat(adapter.findTarget(42L)).isEmpty();
	}

	@Test
	void returnsInsertedResultIdWhenRowWasCreated() {
		when(query.getResultList()).thenReturn(List.<Object>of(900L));

		assertThat(adapter.createResultIfAbsent(42L, new ResultCounts(3, 5), AuctionTerminationReason.FORCED))
			.isEqualTo(900L);
	}

	@Test
	void fallsBackToExistingResultIdWhenInsertConflicted() {
		// `ON CONFLICT DO NOTHING`은 충돌 시 RETURNING을 **비운다.** 여기서 기존 id를 못 찾으면
		// 재실행이 예측 정산에 엉뚱한 auctionResultId를 넘긴다.
		when(query.getResultList()).thenReturn(List.of());
		when(query.getSingleResult()).thenReturn(900L);

		assertThat(adapter.createResultIfAbsent(42L, new ResultCounts(0, 0), AuctionTerminationReason.FORCED))
			.isEqualTo(900L);
	}

	@Test
	void mapsOutstandingHoldRows() {
		when(query.getResultList()).thenReturn(List.<Object>of(
			new Object[]{11L, 100L, 5_000L},
			new Object[]{12L, 200L, 3_000L}
		));

		assertThat(adapter.findOutstandingBidHolds(42L))
			.extracting("bidId", "memberId", "remainingHold")
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple(11L, 100L, 5_000L),
				org.assertj.core.groups.Tuple.tuple(12L, 200L, 3_000L)
			);
	}

	@Test
	void mapsBidCounts() {
		when(query.getSingleResult()).thenReturn(new Object[]{3L, 5L});

		assertThat(adapter.countBids(42L)).isEqualTo(new ResultCounts(3, 5));
	}

	@Test
	void marksEndedOnActualEndColumnNotScheduledDeadline() {
		// `ends_at`(예정 마감)과 `ended_at`(실제 종료)은 다른 컬럼이다. 실제로 바꿔 썼다가
		// 리뷰에서 잡혔다(MR !246) — 행 수만 검증하면 안 잡히는 자리라 SQL을 직접 잠근다.
		// 틀리면 참여·주최·낙찰 이력의 COALESCE 정렬에서 센티넬로 올라가 목록 맨 위에 박히고,
		// 예정 마감까지 덮여 사라진다.
		when(query.executeUpdate()).thenReturn(1);

		adapter.markEnded(42L, Instant.parse("2026-07-31T06:00:00Z"));

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(entityManager).createNativeQuery(sql.capture());
		assertThat(sql.getValue())
			.contains("ended_at = CAST(:endedAt AS TIMESTAMPTZ)")
			.doesNotContain("ends_at");
	}
}
