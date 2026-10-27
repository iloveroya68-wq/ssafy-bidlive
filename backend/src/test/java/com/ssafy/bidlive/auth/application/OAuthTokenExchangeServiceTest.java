package com.ssafy.bidlive.auth.application;

import static com.ssafy.bidlive.auth.domain.OAuthTokenExchangeErrorCode.INVALID_LOGIN_CODE;
import static com.ssafy.bidlive.auth.domain.OAuthTokenExchangeErrorCode.LOGIN_CODE_ALREADY_USED;
import static com.ssafy.bidlive.auth.domain.OAuthTokenExchangeErrorCode.LOGIN_CODE_EXPIRED;
import static com.ssafy.bidlive.auth.domain.OAuthTokenExchangeErrorCode.MEMBER_NOT_AVAILABLE;
import static com.ssafy.bidlive.auth.domain.OAuthTokenExchangeErrorCode.MEMBER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** OAuth 로그인 코드 교환 결과와 Refresh Token 세션 등록 정책을 검증한다. */
@ExtendWith(MockitoExtension.class)
class OAuthTokenExchangeServiceTest {

	private static final String LOGIN_CODE = "a".repeat(43);

	@Mock
	private OneTimeLoginCodeService loginCodeService;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RefreshTokenVerifier refreshTokenVerifier;

	@Mock
	private RefreshTokenSessionStore refreshTokenSessionStore;

	private OAuthTokenExchangeService service;

	@BeforeEach
	void setUp() {
		service = new OAuthTokenExchangeService(
			loginCodeService,
			memberRepository,
			jwtTokenProvider,
			refreshTokenVerifier,
			refreshTokenSessionStore
		);
	}

	@Test
	void exchangesCodeAndReturnsStatusRoleAndOnboardingState() {
		Member member = member(25L, null, MemberStatus.SUSPENDED, MemberRole.USER);
		when(loginCodeService.consume(LOGIN_CODE))
			.thenReturn(LoginCodeConsumeResult.consumed(25L));
		when(memberRepository.findById(25L)).thenReturn(Optional.of(member));
		when(jwtTokenProvider.issue(member)).thenReturn(tokens());
		when(refreshTokenVerifier.verify("refresh-token"))
			.thenReturn(new VerifiedRefreshToken(25L, "refresh-id"));

		OAuthTokenExchangeService.OAuthTokenExchangeResult result =
			service.exchange(LOGIN_CODE);

		assertThat(result.accessToken()).isEqualTo("access-token");
		assertThat(result.refreshToken()).isEqualTo("refresh-token");
		assertThat(result.member().memberId()).isEqualTo(25L);
		assertThat(result.member().nickname()).isNull();
		assertThat(result.member().onboardingRequired()).isTrue();
		assertThat(result.member().status()).isEqualTo(MemberStatus.SUSPENDED);
		assertThat(result.member().role()).isEqualTo(MemberRole.USER);
		verify(refreshTokenSessionStore).save(
			new VerifiedRefreshToken(25L, "refresh-id"),
			Duration.ofDays(14)
		);
	}

	@Test
	void returnsOnboardingNotRequiredWhenNicknameExists() {
		Member member = member(25L, "bidder", MemberStatus.ACTIVE, MemberRole.ADMIN);
		when(loginCodeService.consume(LOGIN_CODE))
			.thenReturn(LoginCodeConsumeResult.consumed(25L));
		when(memberRepository.findById(25L)).thenReturn(Optional.of(member));
		when(jwtTokenProvider.issue(member)).thenReturn(tokens());
		when(refreshTokenVerifier.verify("refresh-token"))
			.thenReturn(new VerifiedRefreshToken(25L, "refresh-id"));

		OAuthTokenExchangeService.OAuthTokenExchangeResult result =
			service.exchange(LOGIN_CODE);

		assertThat(result.member().onboardingRequired()).isFalse();
		assertThat(result.member().role()).isEqualTo(MemberRole.ADMIN);
	}

	@Test
	void rejectsMalformedLoginCodeBeforeRedisLookup() {
		assertErrorCode("not-a-login-code", INVALID_LOGIN_CODE);
		verifyNoInteractions(
			loginCodeService,
			memberRepository,
			jwtTokenProvider,
			refreshTokenVerifier,
			refreshTokenSessionStore
		);
	}

	@Test
	void mapsCodeStateToSpecifiedErrors() {
		when(loginCodeService.consume(LOGIN_CODE))
			.thenReturn(LoginCodeConsumeResult.alreadyUsed())
			.thenReturn(LoginCodeConsumeResult.expired())
			.thenReturn(LoginCodeConsumeResult.notFound());

		assertErrorCode(LOGIN_CODE, LOGIN_CODE_ALREADY_USED);
		assertErrorCode(LOGIN_CODE, LOGIN_CODE_EXPIRED);
		assertErrorCode(LOGIN_CODE, INVALID_LOGIN_CODE);
	}

	@Test
	void rejectsCodeWhoseMemberDoesNotExist() {
		when(loginCodeService.consume(LOGIN_CODE))
			.thenReturn(LoginCodeConsumeResult.consumed(25L));
		when(memberRepository.findById(25L)).thenReturn(Optional.empty());

		assertErrorCode(LOGIN_CODE, MEMBER_NOT_FOUND);
	}

	@Test
	void rejectsWithdrawnMember() {
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(MemberStatus.WITHDRAWN);
		when(loginCodeService.consume(LOGIN_CODE))
			.thenReturn(LoginCodeConsumeResult.consumed(25L));
		when(memberRepository.findById(25L)).thenReturn(Optional.of(member));

		assertErrorCode(LOGIN_CODE, MEMBER_NOT_AVAILABLE);
		verifyNoInteractions(jwtTokenProvider);
	}

	private void assertErrorCode(
		String loginCode,
		com.ssafy.bidlive.auth.domain.OAuthTokenExchangeErrorCode expected
	) {
		assertThatThrownBy(() -> service.exchange(loginCode))
			.isInstanceOfSatisfying(
				OAuthTokenExchangeException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(expected)
			);
	}

	private Member member(
		Long id,
		String nickname,
		MemberStatus status,
		MemberRole role
	) {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(id);
		when(member.getNickname()).thenReturn(nickname);
		when(member.getStatus()).thenReturn(status);
		when(member.getRole()).thenReturn(role);
		return member;
	}

	private JwtTokenProvider.IssuedTokens tokens() {
		return new JwtTokenProvider.IssuedTokens(
			"access-token",
			"refresh-token",
			Duration.ofMinutes(30),
			Duration.ofDays(14)
		);
	}
}
