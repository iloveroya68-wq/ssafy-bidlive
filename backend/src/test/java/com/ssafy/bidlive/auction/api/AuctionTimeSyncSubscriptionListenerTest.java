package com.ssafy.bidlive.auction.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionTimeSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/** 메인 경매 토픽의 인증 구독만 즉시 TIME_SYNC로 연결되는지 검증한다. */
class AuctionTimeSyncSubscriptionListenerTest {

	private AuctionTimeSyncService timeSyncService;
	private AuctionTimeSyncSubscriptionListener listener;

	@BeforeEach
	void setUp() {
		timeSyncService = mock(AuctionTimeSyncService.class);
		listener = new AuctionTimeSyncSubscriptionListener(timeSyncService);
	}

	@Test
	void synchronizesAfterAuthenticatedMainTopicSubscription() {
		listener.onSubscribe(event("/topic/auctions/42", true));

		verify(timeSyncService).synchronizeIfLive(42L);
	}

	@Test
	void ignoresSubtopicsUserQueuesAndUnauthenticatedSubscription() {
		List.of(
			"/topic/auctions/42/chat",
			"/topic/auctions/42/viewers",
			"/topic/auctions/42/bids",
			"/topic/auctions/42/questions",
			"/topic/auctions/42/",
			"/user/queue/errors"
		).forEach(destination ->
			listener.onSubscribe(event(destination, true))
		);
		listener.onSubscribe(event("/topic/auctions/42", false));

		verifyNoInteractions(timeSyncService);
	}

	@Test
	void isolatesSubscriptionFromTimeSyncFailure() {
		when(timeSyncService.synchronizeIfLive(42L))
			.thenThrow(new IllegalStateException("redis unavailable"));

		assertThatCode(() ->
			listener.onSubscribe(event("/topic/auctions/42", true))
		).doesNotThrowAnyException();
		verify(timeSyncService).synchronizeIfLive(42L);
	}

	private SessionSubscribeEvent event(
		String destination,
		boolean authenticated
	) {
		SessionSubscribeEvent event = mock(SessionSubscribeEvent.class);
		when(event.getMessage()).thenReturn(message(destination, authenticated));
		return event;
	}

	private Message<byte[]> message(
		String destination,
		boolean authenticated
	) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(
			StompCommand.SUBSCRIBE
		);
		accessor.setDestination(destination);
		if (authenticated) {
			Principal principal = () -> "member-7";
			accessor.setUser(principal);
		}
		return MessageBuilder.createMessage(
			new byte[0],
			accessor.getMessageHeaders()
		);
	}
}
