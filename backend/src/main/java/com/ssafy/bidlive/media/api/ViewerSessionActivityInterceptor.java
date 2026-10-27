package com.ssafy.bidlive.media.api;

import com.ssafy.bidlive.media.application.ViewerSessionTrackingService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/** heartbeat를 포함한 STOMP 활동마다 이미 구독 중인 경매 세션의 lastSeen을 갱신합니다. */
@Component
public class ViewerSessionActivityInterceptor implements ChannelInterceptor {

	private final ViewerSessionTrackingService trackingService;

	public ViewerSessionActivityInterceptor(
		ViewerSessionTrackingService trackingService
	) {
		this.trackingService = trackingService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
			message,
			StompHeaderAccessor.class
		);
		if (accessor != null && hasText(accessor.getSessionId())) {
			trackingService.touch(accessor.getSessionId());
		}
		return message;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
