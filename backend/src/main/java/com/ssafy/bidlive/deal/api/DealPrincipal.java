package com.ssafy.bidlive.deal.api;

import com.ssafy.bidlive.deal.application.DealException;
import com.ssafy.bidlive.deal.domain.DealErrorCode;
import org.springframework.security.core.Authentication;

/**
 * 인증 주체에서 회원 ID를 꺼낸다. 거래 API 네 개가 같은 방식으로 꺼내므로 한곳에 둔다.
 *
 * <p>익명 요청은 `AnonymousAuthenticationToken`으로 들어와 {@code isAuthenticated()}가 참이다.
 * 이름이 숫자가 아니라는 점으로 걸러야 하고, 안 걸르면 {@code Long.parseLong}이 터져 500이 된다.
 */
final class DealPrincipal {

	private DealPrincipal() {
	}

	static Long memberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new DealException(DealErrorCode.UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new DealException(DealErrorCode.UNAUTHORIZED);
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new DealException(DealErrorCode.UNAUTHORIZED);
		}
	}
}
