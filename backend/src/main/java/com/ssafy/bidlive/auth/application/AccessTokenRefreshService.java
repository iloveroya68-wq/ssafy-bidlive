package com.ssafy.bidlive.auth.application;

import static com.ssafy.bidlive.auth.domain.AccessTokenRefreshErrorCode.MEMBER_UNAVAILABLE;
import static com.ssafy.bidlive.auth.domain.AccessTokenRefreshErrorCode.REFRESH_TOKEN_EXPIRED;
import static com.ssafy.bidlive.auth.domain.AccessTokenRefreshErrorCode.REFRESH_TOKEN_INVALID;
import static com.ssafy.bidlive.auth.domain.AccessTokenRefreshErrorCode.REFRESH_TOKEN_MISSING;

import java.time.Duration;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberStatus;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Refresh Token과 서버 세션을 검증하고 토큰을 회전해 새 Access Token을 발급한다. */
@Service
public class AccessTokenRefreshService {

	/**
	 * 회전 유예 창 (S15P11A704-693). 탭 두 개가 같은 순간에 부팅 복구를 시도하는 정도를 덮으면
	 * 되므로 짧게 잡는다 — 길수록 탈취된 토큰이 재사용될 수 있는 시간도 함께 길어진다.
	 *
	 * <p>설정으로 빼지 않은 이유: `application.yml`이 이 scope의 수정 금지 경로이고, 환경마다
	 * 달라야 할 값도 아니다(브라우저 탭 사이의 경합 폭은 배포 환경과 무관하다).
	 */
	private static final Duration ROTATION_GRACE = Duration.ofSeconds(10);

	private final RefreshTokenVerifier refreshTokenVerifier;
	private final RefreshTokenSessionStore refreshTokenSessionStore;
	private final MemberRepository memberRepository;
	private final JwtTokenProvider jwtTokenProvider;

	public AccessTokenRefreshService(
		RefreshTokenVerifier refreshTokenVerifier,
		RefreshTokenSessionStore refreshTokenSessionStore,
		MemberRepository memberRepository,
		JwtTokenProvider jwtTokenProvider
	) {
		this.refreshTokenVerifier = refreshTokenVerifier;
		this.refreshTokenSessionStore = refreshTokenSessionStore;
		this.memberRepository = memberRepository;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Transactional(readOnly = true)
	public AccessTokenRefreshResult refresh(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new AccessTokenRefreshException(REFRESH_TOKEN_MISSING);
		}

		VerifiedRefreshToken currentToken = verifyOrThrow(refreshToken);
		if (!refreshTokenSessionStore.matches(currentToken)) {
			return replayRotatedOrThrow(currentToken);
		}

		Member member = memberRepository.findById(currentToken.memberId())
			.filter(candidate -> candidate.getStatus() == MemberStatus.ACTIVE)
			.orElseThrow(() -> new AccessTokenRefreshException(MEMBER_UNAVAILABLE));

		JwtTokenProvider.IssuedTokens tokens = jwtTokenProvider.issue(member);
		VerifiedRefreshToken replacementToken = verifyOrThrow(tokens.refreshToken());
		if (!currentToken.memberId().equals(replacementToken.memberId())) {
			throw new AccessTokenRefreshException(REFRESH_TOKEN_INVALID);
		}

		boolean rotated = refreshTokenSessionStore.rotate(
			currentToken,
			replacementToken,
			tokens.refreshToken(),
			tokens.refreshTokenTtl(),
			ROTATION_GRACE
		);
		// **경합에서 지는 지점이 둘이다.** 위 `matches`와 여기 `rotate` — 둘 다 넘어야 통과다.
		// 두 요청이 나란히 `matches`를 통과한 뒤 한쪽이 먼저 회전시키면 나머지는 여기서 0을 받는데,
		// 사실 그때도 세션은 멀쩡하다(이긴 쪽이 회전시켰다). 그래서 같은 구제로 보낸다.
		// 처음엔 `matches` 쪽만 막았다가 재현 테스트에서 그대로 401이 나와 알았다.
		if (!rotated) {
			return replayRotatedOrThrow(currentToken);
		}

		return new AccessTokenRefreshResult(
			tokens.accessToken(),
			tokens.refreshToken(),
			tokens.accessTokenTtl(),
			tokens.refreshTokenTtl()
		);
	}

	/**
	 * 방금 회전된 토큰을 들고 뒤늦게 도착한 요청을 구제한다 (S15P11A704-693).
	 *
	 * <p>회전이 원자적이라 같은 쿠키를 든 두 요청이 겹치면 한쪽만 통과한다. 진 쪽의 401은
	 * <b>세션이 끊긴 것이 아니다</b> — 이긴 쪽이 정상적으로 회전시켰고 브라우저는 새 쿠키를 갖고
	 * 있다. 그런데도 프론트는 그 401을 비로그인으로 확정해 화면이 로그아웃된다. 탭이 둘 이상이면
	 * 실제로 겹친다(프론트 재발급은 탭 안에서만 single-flight다).
	 *
	 * <p><b>새로 회전시키지 않는다.</b> 이긴 쪽이 이미 발급한 Refresh Token을 그대로 돌려준다 —
	 * 여기서 또 회전시키면 사슬이 둘로 갈라져 어느 쪽 쿠키가 살아남는지가 응답 도착 순서에 달린다.
	 * Access Token만 새로 만든다. 그때 함께 나오는 Refresh Token은 <b>저장하지 않으므로 무효</b>이고,
	 * 응답에도 싣지 않는다.
	 *
	 * <p>유예 창 밖이면 원래대로 `REFRESH_TOKEN_INVALID`다 — 진짜로 끊긴 세션과 폐기된 토큰은
	 * 여전히 거절한다.
	 */
	private AccessTokenRefreshResult replayRotatedOrThrow(VerifiedRefreshToken currentToken) {
		String rotatedReplacement = refreshTokenSessionStore
			.findRotatedReplacement(currentToken)
			.orElseThrow(() -> new AccessTokenRefreshException(REFRESH_TOKEN_INVALID));

		Member member = memberRepository.findById(currentToken.memberId())
			.filter(candidate -> candidate.getStatus() == MemberStatus.ACTIVE)
			.orElseThrow(() -> new AccessTokenRefreshException(MEMBER_UNAVAILABLE));

		JwtTokenProvider.IssuedTokens tokens = jwtTokenProvider.issue(member);
		// 돌려주는 Refresh Token은 이긴 쪽이 **유예 창 안에**(수 초 전) 발급한 것이라 남은 수명이
		// 방금 발급분과 사실상 같다. 쿠키 maxAge가 그만큼 길어질 수 있지만, 만료를 실제로 판정하는
		// 것은 토큰의 `exp`라 쿠키가 몇 초 더 남아 있어도 통하지 않는다.
		return new AccessTokenRefreshResult(
			tokens.accessToken(),
			rotatedReplacement,
			tokens.accessTokenTtl(),
			tokens.refreshTokenTtl()
		);
	}

	private VerifiedRefreshToken verifyOrThrow(String refreshToken) {
		try {
			return refreshTokenVerifier.verify(refreshToken);
		} catch (RefreshTokenVerificationException exception) {
			if (exception.getReason() == RefreshTokenVerificationException.Reason.EXPIRED) {
				throw new AccessTokenRefreshException(REFRESH_TOKEN_EXPIRED);
			}
			throw new AccessTokenRefreshException(REFRESH_TOKEN_INVALID);
		}
	}

	public record AccessTokenRefreshResult(
		String accessToken,
		String refreshToken,
		Duration accessTokenTtl,
		Duration refreshTokenTtl
	) {
	}
}
