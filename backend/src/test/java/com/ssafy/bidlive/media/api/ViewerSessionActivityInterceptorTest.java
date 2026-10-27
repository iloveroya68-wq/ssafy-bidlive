package com.ssafy.bidlive.media.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssafy.bidlive.media.application.ViewerSessionTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/** heartbeat를 포함한 inbound frame이 연결을 막지 않고 세션 활동을 갱신하는지 검증합니다. */
class ViewerSessionActivityInterceptorTest {

	private ViewerSessionTrackingService trackingService;
	private ViewerSessionActivityInterceptor interceptor;
	private MessageChannel channel;

	@BeforeEach
	void setUp() {
		trackingService = mock(ViewerSessionTrackingService.class);
		interceptor = new ViewerSessionActivityInterceptor(trackingService);
		channel = mock(MessageChannel.class);
	}

	@Test
	void touchesSessionForHeartbeatAndPassesMessageThrough() {
		StompHeaderAccessor accessor = StompHeaderAccessor.createForHeartbeat();
		accessor.setSessionId("session-1");
		Message<byte[]> message = MessageBuilder.createMessage(
			new byte[0],
			accessor.getMessageHeaders()
		);

		Message<?> result = interceptor.preSend(message, channel);

		assertThat(result).isSameAs(message);
		verify(trackingService).touch("session-1");
	}

	@Test
	void ignoresFrameWithoutSessionId() {
		StompHeaderAccessor accessor = StompHeaderAccessor.createForHeartbeat();
		Message<byte[]> message = MessageBuilder.createMessage(
			new byte[0],
			accessor.getMessageHeaders()
		);

		assertThat(interceptor.preSend(message, channel)).isSameAs(message);
		verify(trackingService, never()).touch(
			org.mockito.ArgumentMatchers.anyString()
		);
	}
}
