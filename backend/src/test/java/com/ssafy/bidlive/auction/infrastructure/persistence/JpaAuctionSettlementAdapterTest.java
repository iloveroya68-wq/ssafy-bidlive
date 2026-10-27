package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** BID-07 대상 조회의 행 잠금과 실제 종료 시각 UPDATE SQL을 검증한다. */
class JpaAuctionSettlementAdapterTest {

	private EntityManager entityManager;
	private Query query;
	private JpaAuctionSettlementAdapter adapter;

	@BeforeEach
	void setUp() {
		entityManager = mock(EntityManager.class);
		query = mock(Query.class);
		when(entityManager.createNativeQuery(anyString())).thenReturn(query);
		when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(query);
		adapter = new JpaAuctionSettlementAdapter(entityManager);
	}

	@Test
	void locksAuctionRoomBeforeSettlementStateDecision() {
		when(query.getResultList()).thenReturn(List.of());

		adapter.lockTarget(100L);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(entityManager).createNativeQuery(sql.capture());
		assertThat(sql.getValue()).contains("FOR UPDATE OF room");
	}

	@Test
	void marksOnlyLiveRoomEndedWithoutChangingScheduledDeadline() {
		when(query.executeUpdate()).thenReturn(1);

		adapter.markEnded(
			100L,
			Instant.parse("2026-07-20T12:20:00Z")
		);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(entityManager).createNativeQuery(sql.capture());
		assertThat(sql.getValue())
			.contains("ended_at = CAST(:endedAt AS TIMESTAMPTZ)")
			.contains("status = :liveStatus")
			.doesNotContain("ends_at =");
	}
}
