package com.ssafy.bidlive.auth.application;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenSessionStore {

	void save(VerifiedRefreshToken token, Duration ttl);

	boolean matches(VerifiedRefreshToken token);

	/**
	 * 기존 세션을 지우고 새 세션으로 원자적으로 교체한다.
	 *
	 * <p>교체와 함께 <b>유예 기록</b>을 남긴다 — 방금 지운 토큰을 들고 뒤늦게 도착한 요청에게
	 * 여기서 발급한 {@code replacementToken}을 그대로 돌려주기 위해서다. 근거는
	 * {@link #findRotatedReplacement(VerifiedRefreshToken)}에 적었다.
	 *
	 * @param replacementTokenValue 쿠키로 나갈 새 Refresh Token 문자열. 유예 기록에 담는다
	 */
	boolean rotate(
		VerifiedRefreshToken currentToken,
		VerifiedRefreshToken replacementToken,
		String replacementTokenValue,
		Duration replacementTtl,
		Duration graceTtl
	);

	/**
	 * 방금 교체된 토큰인가 — 맞으면 그때 발급한 Refresh Token을 돌려준다 (S15P11A704-693).
	 *
	 * <p><b>왜 필요한가.</b> 교체가 원자적이라 같은 쿠키를 든 두 요청이 겹치면 한쪽만 통과하고
	 * 나머지는 「토큰이 올바르지 않다」로 떨어진다. 그런데 그 실패는 <b>세션이 끊긴 것이 아니다</b> —
	 * 이긴 쪽이 이미 정상적으로 회전시켰고 브라우저는 새 쿠키를 갖고 있다. 그런데도 프론트는 그
	 * 401을 비로그인으로 확정해 화면이 로그아웃된다.
	 *
	 * <p>탭이 둘 이상이면 실제로 겹친다. 프론트의 재발급은 <b>탭 안에서만</b> single-flight이고
	 * 탭 사이에는 조율이 없어서, 창 두 개를 같이 새로고침하면 한쪽이 튕긴다.
	 *
	 * <p>⚠️ <b>보안 트레이드오프.</b> 유예 창 동안에는 이미 회전된 토큰이 다시 통해서, 탈취된
	 * 토큰도 그 몇 초 안에는 재사용된다. 대신 창을 짧게 두고, 새 토큰을 <b>발급하지 않고</b>
	 * 이미 발급된 것을 그대로 돌려줘 회전 사슬이 갈라지지 않게 한다.
	 *
	 * @return 유예 창 안이면 그때 발급한 Refresh Token, 아니면 빈 값
	 */
	Optional<String> findRotatedReplacement(VerifiedRefreshToken rotatedToken);

	void delete(VerifiedRefreshToken token);
}
