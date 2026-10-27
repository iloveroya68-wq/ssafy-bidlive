package com.ssafy.bidlive.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;

final class OAuth2SessionCleaner {

	private OAuth2SessionCleaner() {
	}

	static void clear(HttpServletRequest request) {
		SecurityContextHolder.clearContext();
		HttpSession session = request.getSession(false);
		if (session == null) {
			return;
		}
		try {
			session.invalidate();
		} catch (IllegalStateException ignored) {
			// The container may already have invalidated a failed OAuth session.
		}
	}
}
