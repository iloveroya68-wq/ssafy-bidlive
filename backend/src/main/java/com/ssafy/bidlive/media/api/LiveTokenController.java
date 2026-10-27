package com.ssafy.bidlive.media.api;

import com.ssafy.bidlive.media.api.dto.LiveTokenResponse;
import com.ssafy.bidlive.media.application.LiveSessionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 방 단위 라이브 방송의 송출·시청 토큰 발급 요청을 받는 진입점입니다. */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/live")
public class LiveTokenController {

	private final LiveSessionService liveSessionService;

	public LiveTokenController(LiveSessionService liveSessionService) {
		this.liveSessionService = liveSessionService;
	}

	/** 발급자를 방송 개시자로 기록해야 하므로 인증 회원 식별자를 함께 전달한다. */
	@PostMapping("/publisher")
	public LiveTokenResponse issuePublisherToken(
		@PathVariable String roomId,
		Authentication authentication
	) {
		return LiveTokenResponse.from(liveSessionService.issuePublisherToken(
			roomId,
			LiveRequestAuthentication.memberId(authentication)
		));
	}

	/** 시청 자격도 방 공개 범위에 따라 갈리므로 인증 회원 식별자를 함께 전달한다. */
	@PostMapping("/subscriber")
	public LiveTokenResponse issueSubscriberToken(
		@PathVariable String roomId,
		Authentication authentication
	) {
		return LiveTokenResponse.from(liveSessionService.issueSubscriberToken(
			roomId,
			LiveRequestAuthentication.memberId(authentication)
		));
	}
}
