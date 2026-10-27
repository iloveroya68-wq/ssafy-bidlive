package com.ssafy.bidlive.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access Token 필터가 검증된 회원 ID와 역할만 인증 컨텍스트에 등록하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void authenticatesValidBearerAccessToken() throws ServletException, IOException {
		when(jwtTokenProvider.verifyAccessToken("valid-token"))
			.thenReturn(new JwtTokenProvider.VerifiedAccessToken(25L, "USER"));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer valid-token");

		new JwtAuthenticationFilter(jwtTokenProvider).doFilter(
			request,
			new MockHttpServletResponse(),
			new MockFilterChain()
		);

		assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
			.isEqualTo("25");
		assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
			.extracting(Object::toString)
			.containsExactly("ROLE_USER");
		verify(jwtTokenProvider).verifyAccessToken("valid-token");
	}

	@Test
	void leavesRequestUnauthenticatedWhenAccessTokenIsInvalid() throws ServletException, IOException {
		when(jwtTokenProvider.verifyAccessToken("invalid-token"))
			.thenThrow(new IllegalArgumentException("invalid"));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer invalid-token");

		new JwtAuthenticationFilter(jwtTokenProvider).doFilter(
			request,
			new MockHttpServletResponse(),
			new MockFilterChain()
		);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}
}
