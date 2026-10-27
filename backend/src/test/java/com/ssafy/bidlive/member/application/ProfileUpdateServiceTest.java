package com.ssafy.bidlive.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.ssafy.bidlive.member.application.port.out.NicknameProhibitedWordSource;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.ProfileUpdateErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 프로필 저장 검증과 필수 약관 기반 온보딩 상태 계산을 검증한다. */
@ExtendWith(MockitoExtension.class)
class ProfileUpdateServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-28T06:00:00Z");

	@Mock
	private MemberPersistence memberPersistence;

	@Mock
	private NicknameProhibitedWordSource prohibitedWordSource;

	@Mock
	private OnboardingStatusService onboardingStatusService;

	@Mock
	private Member member;

	private ProfileUpdateService service;

	@BeforeEach
	void setUp() {
		service = new ProfileUpdateService(
			memberPersistence,
			prohibitedWordSource,
			onboardingStatusService
		);
	}

	@Test
	void rejectsEmptyUpdateRequest() {
		assertError(
			() -> service.update(25L, null),
			ProfileUpdateErrorCode.EMPTY_UPDATE_REQUEST
		);
		assertError(
			() -> service.update(25L, new ProfileUpdateCommand(null)),
			ProfileUpdateErrorCode.EMPTY_UPDATE_REQUEST
		);
	}

	@Test
	void rejectsInvalidFormatAndProhibitedWordBeforeMemberLookup() {
		assertError(
			() -> service.update(25L, new ProfileUpdateCommand("닉 네임")),
			ProfileUpdateErrorCode.INVALID_NICKNAME
		);

		when(prohibitedWordSource.getActiveWords()).thenReturn(Set.of("admin"));
		assertError(
			() -> service.update(25L, new ProfileUpdateCommand("BestAdmin1")),
			ProfileUpdateErrorCode.INVALID_NICKNAME
		);
		verify(memberPersistence, never()).findByIdForUpdate(25L);
	}

	@Test
	void rejectsMissingMemberAndDuplicateNickname() {
		when(prohibitedWordSource.getActiveWords()).thenReturn(Set.of());
		when(memberPersistence.findByIdForUpdate(25L)).thenReturn(Optional.empty());
		assertError(
			() -> service.update(25L, new ProfileUpdateCommand("희원25")),
			ProfileUpdateErrorCode.MEMBER_NOT_FOUND
		);

		when(memberPersistence.findByIdForUpdate(25L))
			.thenReturn(Optional.of(member));
		when(memberPersistence.existsByNormalizedNicknameAndIdNot("heewon", 25L))
			.thenReturn(true);
		assertError(
			() -> service.update(25L, new ProfileUpdateCommand("HeeWon")),
			ProfileUpdateErrorCode.NICKNAME_ALREADY_EXISTS
		);
	}

	@Test
	void keepsOnboardingRequiredWhenRequiredTermsAreNotAgreed() {
		stubSuccessfulSave("새닉네임", "새닉네임");
		when(onboardingStatusService.isOnboardingRequired(member))
			.thenReturn(true);

		ProfileUpdateResult result = service.update(
			25L,
			new ProfileUpdateCommand("  새닉네임  ")
		);

		assertThat(result.memberId()).isEqualTo(25L);
		assertThat(result.nickname()).isEqualTo("새닉네임");
		assertThat(result.onboardingRequired()).isTrue();
		assertThat(result.updatedAt()).isEqualTo(NOW);
		verify(member).changeNickname("새닉네임");
	}

	@Test
	void completesOnboardingWhenNicknameAndRequiredTermsAreReady() {
		stubSuccessfulSave("HeeWon", "HeeWon");
		when(onboardingStatusService.isOnboardingRequired(member))
			.thenReturn(false);

		ProfileUpdateResult result = service.update(
			25L,
			new ProfileUpdateCommand("HeeWon")
		);

		assertThat(result.onboardingRequired()).isFalse();
		verify(memberPersistence)
			.existsByNormalizedNicknameAndIdNot("heewon", 25L);
	}

	private void stubSuccessfulSave(
		String requestedNickname,
		String savedNickname
	) {
		when(prohibitedWordSource.getActiveWords()).thenReturn(Set.of());
		when(memberPersistence.findByIdForUpdate(25L))
			.thenReturn(Optional.of(member));
		when(memberPersistence.existsByNormalizedNicknameAndIdNot(
			requestedNickname.toLowerCase(),
			25L
		)).thenReturn(false);
		when(memberPersistence.saveProfile(member)).thenReturn(member);
		when(member.getId()).thenReturn(25L);
		when(member.getNickname()).thenReturn(savedNickname);
		when(member.getUpdatedAt()).thenReturn(NOW);
	}

	private void assertError(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
		ProfileUpdateErrorCode expected
	) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(
				ProfileUpdateException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(expected)
			);
	}
}
