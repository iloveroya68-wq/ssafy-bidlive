package com.ssafy.bidlive.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.ssafy.bidlive.deal.application.MemberDealActivity;
import com.ssafy.bidlive.deal.application.MemberDealActivityQuery;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;
import com.ssafy.bidlive.member.domain.ProfileQueryErrorCode;
import com.ssafy.bidlive.prediction.application.MemberPredictionActivity;
import com.ssafy.bidlive.prediction.application.MemberPredictionActivityQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 회원 상태·온보딩과 도메인별 활동 집계를 조합하는 프로필 조회 정책을 검증한다. */
@ExtendWith(MockitoExtension.class)
class ProfileQueryServiceTest {

	@Mock
	private MemberPersistence memberPersistence;

	@Mock
	private OnboardingStatusService onboardingStatusService;

	@Mock
	private MemberDealActivityQuery dealActivityQuery;

	@Mock
	private MemberPredictionActivityQuery predictionActivityQuery;

	@Mock
	private Member member;

	private ProfileQueryService service;

	@BeforeEach
	void setUp() {
		service = new ProfileQueryService(
			memberPersistence,
			onboardingStatusService,
			dealActivityQuery,
			predictionActivityQuery
		);
	}

	@Test
	void returnsLatestMemberInformationAndRoundedActivitySummary() {
		stubAvailableMember(MemberStatus.ACTIVE);
		when(dealActivityQuery.query(25L))
			.thenReturn(new MemberDealActivity(3, 7));
		when(predictionActivityQuery.query(25L))
			.thenReturn(new MemberPredictionActivity(13, 8));

		ProfileQueryResult result = service.query(25L);

		assertThat(result.memberId()).isEqualTo(25L);
		assertThat(result.nickname()).isEqualTo("준호");
		assertThat(result.status()).isEqualTo(MemberStatus.ACTIVE);
		assertThat(result.role()).isEqualTo(MemberRole.USER);
		assertThat(result.activitySummary().salesCount()).isEqualTo(3);
		assertThat(result.activitySummary().purchaseCount()).isEqualTo(7);
		assertThat(result.activitySummary().predictionParticipationCount())
			.isEqualTo(13);
		assertThat(result.activitySummary().predictionHitCount()).isEqualTo(8);
		assertThat(result.activitySummary().predictionHitRate()).isEqualTo(61.54);
	}

	@Test
	void allowsSuspendedMemberAndReturnsZeroHitRateWithoutParticipation() {
		stubAvailableMember(MemberStatus.SUSPENDED);
		when(dealActivityQuery.query(25L))
			.thenReturn(new MemberDealActivity(0, 0));
		when(predictionActivityQuery.query(25L))
			.thenReturn(new MemberPredictionActivity(0, 0));

		ProfileQueryResult result = service.query(25L);

		assertThat(result.status()).isEqualTo(MemberStatus.SUSPENDED);
		assertThat(result.activitySummary().predictionHitRate()).isZero();
	}

	@Test
	void rejectsMissingWithdrawnAndOnboardingMember() {
		when(memberPersistence.findById(25L)).thenReturn(Optional.empty());
		assertError(
			() -> service.query(25L),
			ProfileQueryErrorCode.MEMBER_NOT_FOUND
		);

		when(memberPersistence.findById(25L)).thenReturn(Optional.of(member));
		when(member.getStatus()).thenReturn(MemberStatus.WITHDRAWN);
		assertError(
			() -> service.query(25L),
			ProfileQueryErrorCode.MEMBER_NOT_AVAILABLE
		);
		verify(onboardingStatusService, never()).isOnboardingRequired(member);

		when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
		when(onboardingStatusService.isOnboardingRequired(member))
			.thenReturn(true);
		assertError(
			() -> service.query(25L),
			ProfileQueryErrorCode.ONBOARDING_NOT_COMPLETED
		);
	}

	@Test
	void convertsActivityQueryFailureToProfileSummaryError() {
		when(memberPersistence.findById(25L)).thenReturn(Optional.of(member));
		when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
		when(onboardingStatusService.isOnboardingRequired(member))
			.thenReturn(false);
		when(dealActivityQuery.query(25L))
			.thenThrow(new IllegalStateException("database unavailable"));

		assertError(
			() -> service.query(25L),
			ProfileQueryErrorCode.PROFILE_SUMMARY_QUERY_FAILED
		);
	}

	@Test
	void rejectsInvalidAuthenticatedMemberId() {
		assertError(
			() -> service.query(null),
			ProfileQueryErrorCode.UNAUTHORIZED
		);
		assertError(
			() -> service.query(0L),
			ProfileQueryErrorCode.UNAUTHORIZED
		);
	}

	private void stubAvailableMember(MemberStatus status) {
		when(memberPersistence.findById(25L)).thenReturn(Optional.of(member));
		when(member.getId()).thenReturn(25L);
		when(member.getNickname()).thenReturn("준호");
		when(member.getStatus()).thenReturn(status);
		when(member.getRole()).thenReturn(MemberRole.USER);
		when(onboardingStatusService.isOnboardingRequired(member))
			.thenReturn(false);
	}

	private void assertError(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
		ProfileQueryErrorCode expected
	) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(
				ProfileQueryException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(expected)
			);
	}
}
