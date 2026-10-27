package com.ssafy.bidlive.media.api;

import com.ssafy.bidlive.media.application.LiveBroadcastEndService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 방송 개시자의 라이브 방송 종료 요청을 받는 진입점입니다. */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/live")
public class LiveBroadcastEndController {

	private final LiveBroadcastEndService liveBroadcastEndService;

	public LiveBroadcastEndController(LiveBroadcastEndService liveBroadcastEndService) {
		this.liveBroadcastEndService = liveBroadcastEndService;
	}

	@PostMapping("/end")
	public ResponseEntity<Void> endBroadcast(
		@PathVariable String roomId,
		Authentication authentication
	) {
		liveBroadcastEndService.endBroadcast(
			roomId,
			LiveRequestAuthentication.memberId(authentication)
		);
		return ResponseEntity.noContent().build();
	}
}
