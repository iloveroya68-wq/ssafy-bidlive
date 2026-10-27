package com.ssafy.bidlive.prediction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionAchievementPersistence.Badge;
import com.ssafy.bidlive.prediction.application.PredictionAchievementPersistence.Grade;
import com.ssafy.bidlive.prediction.application.PredictionAchievementPersistence.Statistics;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** PRED-04 JPA 조회가 회원 조건과 정산 집계 규칙을 적용하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class JpaPredictionAchievementPersistenceAdapterTest {

	@Mock
	private EntityManager entityManager;

	@Mock
	private Query query;

	private JpaPredictionAchievementPersistenceAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new JpaPredictionAchievementPersistenceAdapter(entityManager);
		when(entityManager.createNativeQuery(anyString())).thenReturn(query);
		when(query.setParameter("memberId", 25L)).thenReturn(query);
	}

	@Test
	void aggregatesOnlyHitAndMissPredictionResultsForMember() {
		when(query.getSingleResult())
			.thenReturn(new Object[] {18L, 7L, 9_200L, 13_400L});

		Statistics statistics = adapter.findStatistics(25L);

		assertThat(statistics).isEqualTo(
			new Statistics(18L, 7L, 9_200L, 13_400L)
		);
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(entityManager).createNativeQuery(sql.capture());
		assertThat(sql.getValue())
			.contains("pr.result_status IN ('HIT', 'MISS')")
			.contains("pr.result_status = 'HIT'")
			.contains("JOIN auction_participant ap")
			.contains("ap.member_id = :memberId");
	}

	@Test
	void returnsStoredGrade() {
		when(query.getResultList()).thenReturn(
			List.<Object[]>of(new Object[] {"ORACLE_2", 720L})
		);

		assertThat(adapter.findGrade(25L))
			.contains(new Grade("ORACLE_2", 720L));
	}

	@Test
	void returnsEmptyWhenGradeIsNotStored() {
		when(query.getResultList()).thenReturn(List.of());

		assertThat(adapter.findGrade(25L)).isEmpty();
	}

	@Test
	void returnsBadgesInRepositoryOrderAndConvertsInstantTimestamp() {
		OffsetDateTime recent = OffsetDateTime.parse("2026-07-20T21:30:00+09:00");
		Instant old = Instant.parse("2026-07-01T00:00:00Z");
		when(query.getResultList()).thenReturn(List.of(
			new Object[] {"FIRST_HIT", recent},
			new Object[] {"EARLY_ORACLE", old}
		));

		assertThat(adapter.findBadges(25L)).containsExactly(
			new Badge("FIRST_HIT", recent),
			new Badge("EARLY_ORACLE", old.atOffset(ZoneOffset.UTC))
		);
	}
}
