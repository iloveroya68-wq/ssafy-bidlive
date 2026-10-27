package com.ssafy.bidlive.common.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bearer Access Token을 검증해 인증 회원 식별자와 역할을 SecurityContext에 등록한다. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			authenticate(request);
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(HttpServletRequest request) {
		String accessToken = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
		if (accessToken == null) {
			return;
		}

		try {
			JwtTokenProvider.VerifiedAccessToken verifiedToken =
				jwtTokenProvider.verifyAccessToken(accessToken);
			UsernamePasswordAuthenticationToken authentication =
				UsernamePasswordAuthenticationToken.authenticated(
					verifiedToken.memberId().toString(),
					accessToken,
					List.of(new SimpleGrantedAuthority("ROLE_" + verifiedToken.role()))
				);
			authentication.setDetails(
				new WebAuthenticationDetailsSource().buildDetails(request)
			);
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (IllegalArgumentException exception) {
			SecurityContextHolder.clearContext();
		}
	}

	private String bearerToken(String authorizationHeader) {
		if (authorizationHeader == null
			|| !authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			return null;
		}

		String accessToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
		return accessToken.isEmpty() ? null : accessToken;
	}
}
