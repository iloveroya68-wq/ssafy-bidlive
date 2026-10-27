package com.ssafy.bidlive.prediction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.prediction.application.PredictionAchievementPersistence.Badge;
import com.ssafy.bidlive.prediction.application.PredictionAchievementPersistence.Grade;
import com.ssafy.bidlive.prediction.application.PredictionAchievementPersistence.Statistics;
import com.ssafy.bidlive.prediction.domain.PredictionAchievementErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 예측 성과 원본 조회 결과가 PRED-04 응답 값으로 계산되는 정책을 검증한다. */
@ExtendWith(MockitoExtension.class)
class PredictionAchievementQueryServiceTest {

	@Mock
	private MemberPersistence memberPersistence;

	@Mock
	private PredictionAchievementPersistence achievementPersistence;

	private PredictionAchievementQueryService service;

	@BeforeEach
	void setUp() {
		service = new PredictionAchievementQueryService(
			memberPersistence,
			achievementPersistence
		);
	}

	@Test
	void returnsStatisticsGradeAndBadgesForExistingMember() {
		OffsetDateTime earnedAt = OffsetDateTime.parse(
			"2026-07-20T21:30:00+09:00"
		);
		when(memberPersistence.findById(25L))
			.thenReturn(Optional.of(mock(Member.class)));
		when(achievementPersistence.findStatistics(25L))
			.thenReturn(new Statistics(18L, 7L, 9_200L, 13_400L));
		when(achievementPersistence.findGrade(25L))
			.thenReturn(Optional.of(new Grade("ORACLE_2", 720L)));
		when(achievementPersistence.findBadges(25L))
			.thenReturn(List.of(new Badge("FIRST_HIT", earnedAt)));

		PredictionAchievementResult result =
			service.getPredictionAchievements(25L);

		assertThat(result.memberId()).isEqualTo(25L);
		assertThat(result.predictionCount()).isEqualTo(18);
		assertThat(result.hitCount()).isEqualTo(7);
		assertThat(result.hitRate()).isEqualByComparingTo("38.9");
		assertThat(result.totalStakedPoints()).isEqualTo(9_200L);
		assertThat(result.totalRewardPoints()).isEqualTo(13_400L);
		assertThat(result.grade())
			.isEqualTo(new PredictionAchievementResult.Grade(
				"ORACLE_2",
				"시세 예언자 II",
				720L
			));
		assertThat(result.badges()).containsExactly(
			new PredictionAchievementResult.Badge(
				"FIRST_HIT",
				"첫 적중",
				earnedAt
			)
		);
	}

	@Test
	void returnsInitialAchievementWhenMemberHasNoSettlementHistory() {
		when(memberPersistence.findById(25L))
			.thenReturn(Optional.of(mock(Member.class)));
		when(achievementPersistence.findStatistics(25L))
			.thenReturn(new Statistics(0L, 0L, 0L, 0L));
		when(achievementPersistence.findGrade(25L)).thenReturn(Optional.empty());
		when(achievementPersistence.findBadges(25L)).thenReturn(List.of());

		PredictionAchievementResult result =
			service.getPredictionAchievements(25L);

		assertThat(result.predictionCount()).isZero();
		assertThat(result.hitCount()).isZero();
		assertThat(result.hitRate()).isEqualByComparingTo("0.0");
		assertThat(result.totalStakedPoints()).isZero();
		assertThat(result.totalRewardPoints()).isZero();
		assertThat(result.grade())
			.isEqualTo(new PredictionAchievementResult.Grade(
				"ORACLE_1",
				"시세 예언자 I",
				0L
			));
		assertThat(result.badges()).isEmpty();
	}

	@Test
	void rejectsMissingMemberWithoutQueryingAchievements() {
		when(memberPersistence.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getPredictionAchievements(999L))
			.isInstanceOfSatisfying(
				PredictionAchievementException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(PredictionAchievementErrorCode.MEMBER_NOT_FOUND)
			);
		verifyNoInteractions(achievementPersistence);
	}

	@Test
	void rejectsNullMemberIdWithoutQueryingPersistence() {
		assertThatThrownBy(() -> service.getPredictionAchievements(null))
			.isInstanceOfSatisfying(
				PredictionAchievementException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(PredictionAchievementErrorCode.MEMBER_NOT_FOUND)
			);

		verifyNoInteractions(memberPersistence, achievementPersistence);
	}

	@Test
	void roundsHitRateToOneDecimalPlace() {
		when(memberPersistence.findById(25L))
			.thenReturn(Optional.of(mock(Member.class)));
		when(achievementPersistence.findStatistics(25L))
			.thenReturn(new Statistics(6L, 2L, 600L, 1_000L));
		when(achievementPersistence.findGrade(25L))
			.thenReturn(Optional.of(new Grade("ORACLE_1", 200L)));
		when(achievementPersistence.findBadges(25L)).thenReturn(List.of());

		PredictionAchievementResult result =
			service.getPredictionAchievements(25L);

		assertThat(result.hitRate()).isEqualByComparingTo("33.3");
		verify(achievementPersistence).findStatistics(25L);
	}
}
