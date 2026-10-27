package com.ssafy.bidlive.media.api;

import com.ssafy.bidlive.common.security.SecurityPrincipal;
import com.ssafy.bidlive.common.websocket.subscription.StompSubscriptionDestination;
import com.ssafy.bidlive.common.websocket.subscription.StompSubscriptionDestinationResolver;
import com.ssafy.bidlive.media.application.ViewerSessionTrackingService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/** 성공한 STOMP 구독·해제와 연결 종료를 경매 시청 세션 생명주기로 전달합니다. */
@Component
public class ViewerSessionStompEventListener {

	private final StompSubscriptionDestinationResolver destinationResolver;
	private final ViewerSessionTrackingService trackingService;

	public ViewerSessionStompEventListener(
		StompSubscriptionDestinationResolver destinationResolver,
		ViewerSessionTrackingService trackingService
	) {
		this.destinationResolver = destinationResolver;
		this.trackingService = trackingService;
	}

	@EventListener
	public void onSubscribe(SessionSubscribeEvent event) {
		StompHeaderAccessor accessor = accessor(event.getMessage());
		if (accessor == null
			|| !(accessor.getUser() instanceof SecurityPrincipal)
			|| !hasText(accessor.getSessionId())
			|| !hasText(accessor.getSubscriptionId())) {
			return;
		}

		StompSubscriptionDestination destination = destinationResolver.resolve(
			accessor.getDestination()
		);
		if (destination.auctionId() == null) {
			return;
		}

		trackingService.subscribe(
			accessor.getSessionId(),
			accessor.getSubscriptionId(),
			destination.auctionId()
		);
	}

	@EventListener
	public void onUnsubscribe(SessionUnsubscribeEvent event) {
		StompHeaderAccessor accessor = accessor(event.getMessage());
		if (accessor == null
			|| !hasText(accessor.getSessionId())
			|| !hasText(accessor.getSubscriptionId())) {
			return;
		}

		trackingService.unsubscribe(
			accessor.getSessionId(),
			accessor.getSubscriptionId()
		);
	}

	@EventListener
	public void onDisconnect(SessionDisconnectEvent event) {
		if (!hasText(event.getSessionId())) {
			return;
		}
		trackingService.disconnect(event.getSessionId());
	}

	private StompHeaderAccessor accessor(Message<?> message) {
		return MessageHeaderAccessor.getAccessor(
			message,
			StompHeaderAccessor.class
		);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
