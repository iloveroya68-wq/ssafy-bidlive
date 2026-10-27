package com.ssafy.bidlive.auth.application;

import java.time.Duration;
import java.util.regex.Pattern;

import com.ssafy.bidlive.auth.domain.OAuthTokenExchangeErrorCode;
import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일회용 로그인 코드를 JWT로 교환하고 Refresh Token 서버 세션을 등록한다. */
@Service
public class OAuthTokenExchangeService {

	private static final Pattern LOGIN_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

	private final OneTimeLoginCodeService loginCodeService;
	private final MemberRepository memberRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenVerifier refreshTokenVerifier;
	private final RefreshTokenSessionStore refreshTokenSessionStore;

	public OAuthTokenExchangeService(
		OneTimeLoginCodeService loginCodeService,
		MemberRepository memberRepository,
		JwtTokenProvider jwtTokenProvider,
		RefreshTokenVerifier refreshTokenVerifier,
		RefreshTokenSessionStore refreshTokenSessionStore
	) {
		this.loginCodeService = loginCodeService;
		this.memberRepository = memberRepository;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenVerifier = refreshTokenVerifier;
		this.refreshTokenSessionStore = refreshTokenSessionStore;
	}

	@Transactional(readOnly = true)
	public OAuthTokenExchangeResult exchange(String loginCode) {
		validateLoginCode(loginCode);

		LoginCodeConsumeResult consumeResult = loginCodeService.consume(loginCode);
		Long memberId = memberIdOrThrow(consumeResult);
		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new OAuthTokenExchangeException(
				OAuthTokenExchangeErrorCode.MEMBER_NOT_FOUND
			));

		if (member.getStatus() == MemberStatus.WITHDRAWN) {
			throw new OAuthTokenExchangeException(
				OAuthTokenExchangeErrorCode.MEMBER_NOT_AVAILABLE
			);
		}

		JwtTokenProvider.IssuedTokens tokens = jwtTokenProvider.issue(member);
		VerifiedRefreshToken verifiedRefreshToken =
			refreshTokenVerifier.verify(tokens.refreshToken());
		if (!member.getId().equals(verifiedRefreshToken.memberId())) {
			throw new IllegalStateException(
				"Issued refresh token member does not match authenticated member"
			);
		}
		refreshTokenSessionStore.save(
			verifiedRefreshToken,
			tokens.refreshTokenTtl()
		);

		boolean onboardingRequired =
			member.getNickname() == null || member.getNickname().isBlank();

		return new OAuthTokenExchangeResult(
			tokens.accessToken(),
			tokens.refreshToken(),
			tokens.accessTokenTtl(),
			tokens.refreshTokenTtl(),
			new MemberSummary(
				member.getId(),
				member.getNickname(),
				onboardingRequired,
				member.getStatus(),
				member.getRole()
			)
		);
	}

	private void validateLoginCode(String loginCode) {
		if (loginCode == null || !LOGIN_CODE_PATTERN.matcher(loginCode).matches()) {
			throw new OAuthTokenExchangeException(
				OAuthTokenExchangeErrorCode.INVALID_LOGIN_CODE
			);
		}
	}

	private Long memberIdOrThrow(LoginCodeConsumeResult consumeResult) {
		return switch (consumeResult.status()) {
			case CONSUMED -> consumeResult.memberId();
			case ALREADY_USED -> throw new OAuthTokenExchangeException(
				OAuthTokenExchangeErrorCode.LOGIN_CODE_ALREADY_USED
			);
			case EXPIRED -> throw new OAuthTokenExchangeException(
				OAuthTokenExchangeErrorCode.LOGIN_CODE_EXPIRED
			);
			case NOT_FOUND -> throw new OAuthTokenExchangeException(
				OAuthTokenExchangeErrorCode.INVALID_LOGIN_CODE
			);
		};
	}

	public record OAuthTokenExchangeResult(
		String accessToken,
		String refreshToken,
		Duration accessTokenTtl,
		Duration refreshTokenTtl,
		MemberSummary member
	) {
	}

	public record MemberSummary(
		Long memberId,
		String nickname,
		boolean onboardingRequired,
		MemberStatus status,
		MemberRole role
	) {
	}
}
