package com.ssafy.bidlive.auth.application;

import static com.ssafy.bidlive.auth.domain.AccessTokenRefreshErrorCode.MEMBER_UNAVAILABLE;
import static com.ssafy.bidlive.auth.domain.AccessTokenRefreshErrorCode.REFRESH_TOKEN_EXPIRED;
import static com.ssafy.bidlive.auth.domain.AccessTokenRefreshErrorCode.REFRESH_TOKEN_INVALID;
import static com.ssafy.bidlive.auth.domain.AccessTokenRefreshErrorCode.REFRESH_TOKEN_MISSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberStatus;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Refresh Token 검증·회원 상태 확인·원자적 회전에 따른 재발급 결과를 검증한다. */
@ExtendWith(MockitoExtension.class)
class AccessTokenRefreshServiceTest {

	private static final String CURRENT_REFRESH_TOKEN = "current-refresh-token";
	private static final VerifiedRefreshToken CURRENT_TOKEN =
		new VerifiedRefreshToken(25L, "current-id");
	private static final VerifiedRefreshToken REPLACEMENT_TOKEN =
		new VerifiedRefreshToken(25L, "replacement-id");

	@Mock
	private RefreshTokenVerifier refreshTokenVerifier;

	@Mock
	private RefreshTokenSessionStore refreshTokenSessionStore;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	private AccessTokenRefreshService service;

	@BeforeEach
	void setUp() {
		service = new AccessTokenRefreshService(
			refreshTokenVerifier,
			refreshTokenSessionStore,
			memberRepository,
			jwtTokenProvider
		);
	}

	@Test
	void rotatesRefreshSessionAndReturnsNewTokens() {
		Member member = member(MemberStatus.ACTIVE);
		stubValidCurrentSession(member);
		when(jwtTokenProvider.issue(member)).thenReturn(tokens());
		when(refreshTokenVerifier.verify("replacement-refresh-token"))
			.thenReturn(REPLACEMENT_TOKEN);
		when(refreshTokenSessionStore.rotate(
			CURRENT_TOKEN,
			REPLACEMENT_TOKEN,
			"replacement-refresh-token",
			Duration.ofDays(14),
			Duration.ofSeconds(10)
		)).thenReturn(true);

		AccessTokenRefreshService.AccessTokenRefreshResult result =
			service.refresh(CURRENT_REFRESH_TOKEN);

		assertThat(result.accessToken()).isEqualTo("replacement-access-token");
		assertThat(result.refreshToken()).isEqualTo("replacement-refresh-token");
		assertThat(result.accessTokenTtl()).isEqualTo(Duration.ofMinutes(30));
		verify(refreshTokenSessionStore).rotate(
			CURRENT_TOKEN,
			REPLACEMENT_TOKEN,
			"replacement-refresh-token",
			Duration.ofDays(14),
			Duration.ofSeconds(10)
		);
	}

	@Test
	void rejectsMissingCookieBeforeTokenVerification() {
		assertErrorCode(null, REFRESH_TOKEN_MISSING);
		assertErrorCode(" ", REFRESH_TOKEN_MISSING);
		verifyNoInteractions(
			refreshTokenVerifier,
			refreshTokenSessionStore,
			memberRepository,
			jwtTokenProvider
		);
	}

	@Test
	void mapsExpiredJwtToSpecifiedError() {
		when(refreshTokenVerifier.verify(CURRENT_REFRESH_TOKEN)).thenThrow(
			new RefreshTokenVerificationException(
				RefreshTokenVerificationException.Reason.EXPIRED,
				new RuntimeException("expired")
			)
		);

		assertErrorCode(CURRENT_REFRESH_TOKEN, REFRESH_TOKEN_EXPIRED);
		verifyNoInteractions(refreshTokenSessionStore, memberRepository, jwtTokenProvider);
	}

	@Test
	void mapsInvalidJwtToSpecifiedError() {
		when(refreshTokenVerifier.verify(CURRENT_REFRESH_TOKEN)).thenThrow(
			new RefreshTokenVerificationException(
				RefreshTokenVerificationException.Reason.INVALID,
				new RuntimeException("invalid")
			)
		);

		assertErrorCode(CURRENT_REFRESH_TOKEN, REFRESH_TOKEN_INVALID);
		verifyNoInteractions(refreshTokenSessionStore, memberRepository, jwtTokenProvider);
	}

	@Test
	void rejectsSessionThatIsNotStoredInRedis() {
		when(refreshTokenVerifier.verify(CURRENT_REFRESH_TOKEN))
			.thenReturn(CURRENT_TOKEN);
		when(refreshTokenSessionStore.matches(CURRENT_TOKEN)).thenReturn(false);

		assertErrorCode(CURRENT_REFRESH_TOKEN, REFRESH_TOKEN_INVALID);
		verifyNoInteractions(memberRepository, jwtTokenProvider);
	}

	@ParameterizedTest
	@EnumSource(
		value = MemberStatus.class,
		names = {"SUSPENDED", "WITHDRAWN"}
	)
	void rejectsUnavailableMember(MemberStatus status) {
		Member member = member(status);
		stubValidCurrentSession(member);

		assertErrorCode(CURRENT_REFRESH_TOKEN, MEMBER_UNAVAILABLE);
		verifyNoInteractions(jwtTokenProvider);
	}

	@Test
	void rejectsRefreshTokenWhenMemberDoesNotExist() {
		when(refreshTokenVerifier.verify(CURRENT_REFRESH_TOKEN))
			.thenReturn(CURRENT_TOKEN);
		when(refreshTokenSessionStore.matches(CURRENT_TOKEN)).thenReturn(true);
		when(memberRepository.findById(25L)).thenReturn(Optional.empty());

		assertErrorCode(CURRENT_REFRESH_TOKEN, MEMBER_UNAVAILABLE);
	}

	@Test
	void rejectsConcurrentReuseWhenAtomicRotationLoses() {
		Member member = member(MemberStatus.ACTIVE);
		stubValidCurrentSession(member);
		when(jwtTokenProvider.issue(member)).thenReturn(tokens());
		when(refreshTokenVerifier.verify("replacement-refresh-token"))
			.thenReturn(REPLACEMENT_TOKEN);
		when(refreshTokenSessionStore.rotate(
			CURRENT_TOKEN,
			REPLACEMENT_TOKEN,
			"replacement-refresh-token",
			Duration.ofDays(14),
			Duration.ofSeconds(10)
		)).thenReturn(false);

		assertErrorCode(CURRENT_REFRESH_TOKEN, REFRESH_TOKEN_INVALID);
	}

	private void stubValidCurrentSession(Member member) {
		when(refreshTokenVerifier.verify(CURRENT_REFRESH_TOKEN))
			.thenReturn(CURRENT_TOKEN);
		when(refreshTokenSessionStore.matches(CURRENT_TOKEN)).thenReturn(true);
		when(memberRepository.findById(25L)).thenReturn(Optional.of(member));
	}

	private Member member(MemberStatus status) {
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(status);
		return member;
	}

	private JwtTokenProvider.IssuedTokens tokens() {
		return new JwtTokenProvider.IssuedTokens(
			"replacement-access-token",
			"replacement-refresh-token",
			Duration.ofMinutes(30),
			Duration.ofDays(14)
		);
	}

	private void assertErrorCode(
		String refreshToken,
		com.ssafy.bidlive.auth.domain.AccessTokenRefreshErrorCode expected
	) {
		assertThatThrownBy(() -> service.refresh(refreshToken))
			.isInstanceOfSatisfying(
				AccessTokenRefreshException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(expected)
			);
	}

	// ── 회전 유예 창 (S15P11A704-693) ──
	//
	// 회전이 원자적이라 같은 쿠키를 든 두 요청이 겹치면 한쪽이 401을 받는데, 그건 세션이 끊긴 것이
	// 아니라 **이긴 쪽이 이미 정상 회전시킨** 상태다. 프론트는 그 401을 비로그인으로 확정하므로
	// 탭 두 개를 같이 새로고침하면 한쪽이 로그아웃된다.

	@Test
	void replaysAlreadyRotatedTokenWithinGraceWindow() {
		Member member = member(MemberStatus.ACTIVE);
		when(refreshTokenVerifier.verify(CURRENT_REFRESH_TOKEN)).thenReturn(CURRENT_TOKEN);
		when(refreshTokenSessionStore.matches(CURRENT_TOKEN)).thenReturn(false);
		when(refreshTokenSessionStore.findRotatedReplacement(CURRENT_TOKEN))
			.thenReturn(Optional.of("winner-refresh-token"));
		when(memberRepository.findById(25L)).thenReturn(Optional.of(member));
		when(jwtTokenProvider.issue(member)).thenReturn(tokens());

		AccessTokenRefreshService.AccessTokenRefreshResult result =
			service.refresh(CURRENT_REFRESH_TOKEN);

		// **이긴 쪽의 토큰을 그대로** 돌려준다 — 여기서 새로 발급하면 회전 사슬이 둘로 갈라져
		// 어느 쪽 쿠키가 살아남는지가 응답 도착 순서에 달린다.
		assertThat(result.refreshToken()).isEqualTo("winner-refresh-token");
		assertThat(result.accessToken()).isEqualTo("replacement-access-token");
	}

	@Test
	void doesNotRotateAgainWhenReplayingWithinGraceWindow() {
		Member member = member(MemberStatus.ACTIVE);
		when(refreshTokenVerifier.verify(CURRENT_REFRESH_TOKEN)).thenReturn(CURRENT_TOKEN);
		when(refreshTokenSessionStore.matches(CURRENT_TOKEN)).thenReturn(false);
		when(refreshTokenSessionStore.findRotatedReplacement(CURRENT_TOKEN))
			.thenReturn(Optional.of("winner-refresh-token"));
		when(memberRepository.findById(25L)).thenReturn(Optional.of(member));
		when(jwtTokenProvider.issue(member)).thenReturn(tokens());

		service.refresh(CURRENT_REFRESH_TOKEN);

		verify(refreshTokenSessionStore, never())
			.rotate(any(), any(), any(), any(), any());
	}

	/** 유예 창 밖이면 원래대로 거절한다 — 진짜 끊긴 세션과 폐기된 토큰까지 통과시키면 안 된다. */
	@Test
	void stillRejectsUnknownTokenOutsideGraceWindow() {
		when(refreshTokenVerifier.verify(CURRENT_REFRESH_TOKEN)).thenReturn(CURRENT_TOKEN);
		when(refreshTokenSessionStore.matches(CURRENT_TOKEN)).thenReturn(false);
		when(refreshTokenSessionStore.findRotatedReplacement(CURRENT_TOKEN))
			.thenReturn(Optional.empty());

		assertErrorCode(CURRENT_REFRESH_TOKEN, REFRESH_TOKEN_INVALID);
		verifyNoInteractions(jwtTokenProvider);
	}

	/** 유예 창 안이어도 정지·탈퇴 회원은 막는다 — 회전 경합 구제가 회원 상태 게이트를 뚫으면 안 된다. */
	@Test
	void rejectsInactiveMemberEvenWithinGraceWindow() {
		when(refreshTokenVerifier.verify(CURRENT_REFRESH_TOKEN)).thenReturn(CURRENT_TOKEN);
		when(refreshTokenSessionStore.matches(CURRENT_TOKEN)).thenReturn(false);
		when(refreshTokenSessionStore.findRotatedReplacement(CURRENT_TOKEN))
			.thenReturn(Optional.of("winner-refresh-token"));
		// 헬퍼가 내부에서 stubbing을 하므로 `when(...)` 인자 안에서 부르면 중첩 stubbing이 된다
		Member suspended = member(MemberStatus.SUSPENDED);
		when(memberRepository.findById(25L)).thenReturn(Optional.of(suspended));

		assertErrorCode(CURRENT_REFRESH_TOKEN, MEMBER_UNAVAILABLE);
	}
}
