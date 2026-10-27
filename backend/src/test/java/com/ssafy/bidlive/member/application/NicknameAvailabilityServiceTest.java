package com.ssafy.bidlive.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import com.ssafy.bidlive.member.application.port.out.NicknameProhibitedWordSource;
import com.ssafy.bidlive.member.domain.NicknameAvailabilityErrorCode;
import com.ssafy.bidlive.member.domain.NicknameAvailabilityReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 닉네임 정규화, 형식, 금칙어와 중복 검증 순서를 고정한다. */
@ExtendWith(MockitoExtension.class)
class NicknameAvailabilityServiceTest {

	@Mock
	private MemberPersistence memberPersistence;

	@Mock
	private NicknameProhibitedWordSource prohibitedWordSource;

	@InjectMocks
	private NicknameAvailabilityService service;

	@Test
	void rejectsMissingOrBlankNickname() {
		assertThatThrownBy(() -> service.check(25L, null))
			.isInstanceOfSatisfying(
				NicknameAvailabilityException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(NicknameAvailabilityErrorCode.NICKNAME_REQUIRED)
			);
		assertThatThrownBy(() -> service.check(25L, "   "))
			.isInstanceOfSatisfying(
				NicknameAvailabilityException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(NicknameAvailabilityErrorCode.NICKNAME_REQUIRED)
			);
	}

	@Test
	void returnsInvalidFormatBeforeExternalLookups() {
		NicknameAvailabilityResult result = service.check(25L, "닉 네임!");

		assertThat(result.nickname()).isEqualTo("닉 네임!");
		assertThat(result.available()).isFalse();
		assertThat(result.reason()).isEqualTo(
			NicknameAvailabilityReason.INVALID_FORMAT
		);
		verify(prohibitedWordSource, never()).getActiveWords();
	}

	@Test
	void rejectsNicknameLongerThanThirtyCharacters() {
		NicknameAvailabilityResult result = service.check(25L, "가".repeat(31));

		assertThat(result.available()).isFalse();
		assertThat(result.reason()).isEqualTo(
			NicknameAvailabilityReason.INVALID_FORMAT
		);
	}

	@Test
	void detectsContainedProhibitedWordCaseInsensitively() {
		when(prohibitedWordSource.getActiveWords())
			.thenReturn(Set.of("Admin", "운영자"));

		NicknameAvailabilityResult result = service.check(25L, "bestADMIN1");

		assertThat(result.available()).isFalse();
		assertThat(result.reason()).isEqualTo(
			NicknameAvailabilityReason.PROHIBITED_WORD
		);
		verify(memberPersistence, never())
			.existsByNormalizedNicknameAndIdNot("bestadmin1", 25L);
	}

	@Test
	void detectsAnotherMembersNicknameIgnoringCase() {
		when(prohibitedWordSource.getActiveWords()).thenReturn(Set.of());
		when(memberPersistence.existsByNormalizedNicknameAndIdNot("heewon", 25L))
			.thenReturn(true);

		NicknameAvailabilityResult result = service.check(25L, "  HeeWon  ");

		assertThat(result.nickname()).isEqualTo("HeeWon");
		assertThat(result.available()).isFalse();
		assertThat(result.reason()).isEqualTo(
			NicknameAvailabilityReason.ALREADY_EXISTS
		);
	}

	@Test
	void allowsAvailableNicknameAndExcludesCurrentMemberFromDuplicateLookup() {
		when(prohibitedWordSource.getActiveWords()).thenReturn(Set.of());
		when(memberPersistence.existsByNormalizedNicknameAndIdNot("희원25", 25L))
			.thenReturn(false);

		NicknameAvailabilityResult result = service.check(25L, " 희원25 ");

		assertThat(result.nickname()).isEqualTo("희원25");
		assertThat(result.available()).isTrue();
		assertThat(result.reason()).isNull();
		verify(memberPersistence)
			.existsByNormalizedNicknameAndIdNot("희원25", 25L);
	}
}
