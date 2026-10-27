package com.ssafy.bidlive.common.observability;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** STOMP 연결 이벤트 중복 제거와 활성 세션 수 갱신 동작을 검증합니다. */
class StompSessionMetricsListenerTest {

	@Test
	void countsEachConnectedSessionOnceAndRemovesItOnDisconnect() {
		RealtimeObservationMetrics metrics = mock(
			RealtimeObservationMetrics.class
		);
		StompSessionMetricsListener listener =
			new StompSessionMetricsListener(metrics);
		SessionConnectedEvent connected = connectedEvent("session-1");

		listener.onConnected(connected);
		listener.onConnected(connected);

		verify(metrics, times(1)).recordConnectSucceeded();
		verify(metrics, times(1)).setActiveStompSessions(1);

		SessionDisconnectEvent disconnected = mock(
			SessionDisconnectEvent.class
		);
		when(disconnected.getSessionId()).thenReturn("session-1");
		listener.onDisconnected(disconnected);
		listener.onDisconnected(disconnected);

		verify(metrics, times(1)).setActiveStompSessions(0);
	}

	@SuppressWarnings("unchecked")
	private SessionConnectedEvent connectedEvent(String sessionId) {
		SessionConnectedEvent event = mock(SessionConnectedEvent.class);
		Message<byte[]> message = MessageBuilder.withPayload(new byte[0])
			.setHeader("simpSessionId", sessionId)
			.build();
		when(event.getMessage()).thenReturn((Message) message);
		return event;
	}
}
