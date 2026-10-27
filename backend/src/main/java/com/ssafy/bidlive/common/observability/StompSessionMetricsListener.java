package com.ssafy.bidlive.common.observability;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** 연결·해제 이벤트를 중복 없이 집계해 현재 STOMP 세션 수를 유지합니다. */
@Component
public class StompSessionMetricsListener {

	private final RealtimeObservationMetrics metrics;
	private final Set<String> connectedSessionIds =
		ConcurrentHashMap.newKeySet();

	public StompSessionMetricsListener(RealtimeObservationMetrics metrics) {
		this.metrics = metrics;
	}

	@EventListener
	public void onConnected(SessionConnectedEvent event) {
		String sessionId = SimpMessageHeaderAccessor.getSessionId(
			event.getMessage().getHeaders()
		);
		if (sessionId != null && connectedSessionIds.add(sessionId)) {
			metrics.recordConnectSucceeded();
			metrics.setActiveStompSessions(connectedSessionIds.size());
		}
	}

	@EventListener
	public void onDisconnected(SessionDisconnectEvent event) {
		if (connectedSessionIds.remove(event.getSessionId())) {
			metrics.setActiveStompSessions(connectedSessionIds.size());
		}
	}
}
