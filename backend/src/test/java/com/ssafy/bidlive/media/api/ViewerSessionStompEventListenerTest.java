package com.ssafy.bidlive.media.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.bidlive.common.security.SecurityPrincipal;
import com.ssafy.bidlive.common.websocket.subscription.StompSubscriptionDestinationResolver;
import com.ssafy.bidlive.media.application.ViewerSessionTrackingService;
import com.ssafy.bidlive.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/** 성공한 STOMP 세션 이벤트만 경매 시청 세션 정책으로 전달되는지 검증합니다. */
class ViewerSessionStompEventListenerTest {

	private ViewerSessionTrackingService trackingService;
	private ViewerSessionStompEventListener listener;

	@BeforeEach
	void setUp() {
		trackingService = mock(ViewerSessionTrackingService.class);
		listener = new ViewerSessionStompEventListener(
			new StompSubscriptionDestinationResolver(),
			trackingService
		);
	}

	@Test
	void registersAuthenticatedAuctionSubscription() {
		SessionSubscribeEvent event = mock(SessionSubscribeEvent.class);
		when(event.getMessage()).thenReturn(
			message(
				StompCommand.SUBSCRIBE,
				"session-1",
				"subscription-1",
				"/topic/auctions/42/viewers",
				new SecurityPrincipal(7L, MemberRole.USER)
			)
		);

		listener.onSubscribe(event);

		verify(trackingService).subscribe(
			"session-1",
			"subscription-1",
			42L
		);
	}

	@Test
	void ignoresUserQueueAndUnauthenticatedAuctionSubscription() {
		SessionSubscribeEvent userQueue = mock(SessionSubscribeEvent.class);
		when(userQueue.getMessage()).thenReturn(
			message(
				StompCommand.SUBSCRIBE,
				"session-1",
				"errors",
				"/user/queue/errors",
				new SecurityPrincipal(7L, MemberRole.USER)
			)
		);
		SessionSubscribeEvent unauthenticated = mock(
			SessionSubscribeEvent.class
		);
		when(unauthenticated.getMessage()).thenReturn(
			message(
				StompCommand.SUBSCRIBE,
				"session-2",
				"viewers",
				"/topic/auctions/42/viewers",
				null
			)
		);

		listener.onSubscribe(userQueue);
		listener.onSubscribe(unauthenticated);

		verify(trackingService, never()).subscribe(
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyLong()
		);
	}

	@Test
	void removesSubscriptionAndDisconnectedSession() {
		SessionUnsubscribeEvent unsubscribe = mock(
			SessionUnsubscribeEvent.class
		);
		when(unsubscribe.getMessage()).thenReturn(
			message(
				StompCommand.UNSUBSCRIBE,
				"session-1",
				"subscription-1",
				null,
				null
			)
		);
		SessionDisconnectEvent disconnect = mock(SessionDisconnectEvent.class);
		when(disconnect.getSessionId()).thenReturn("session-1");

		listener.onUnsubscribe(unsubscribe);
		listener.onDisconnect(disconnect);

		verify(trackingService).unsubscribe(
			"session-1",
			"subscription-1"
		);
		verify(trackingService).disconnect("session-1");
	}

	private Message<byte[]> message(
		StompCommand command,
		String sessionId,
		String subscriptionId,
		String destination,
		SecurityPrincipal principal
	) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setSessionId(sessionId);
		accessor.setSubscriptionId(subscriptionId);
		accessor.setDestination(destination);
		accessor.setUser(principal);
		return MessageBuilder.createMessage(
			new byte[0],
			accessor.getMessageHeaders()
		);
	}
}
