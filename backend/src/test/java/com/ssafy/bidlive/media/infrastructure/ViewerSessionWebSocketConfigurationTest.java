package com.ssafy.bidlive.media.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ssafy.bidlive.media.api.ViewerSessionActivityInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;

/** media 세션 활동 인터셉터가 공통 inbound channel에 등록되는지 검증합니다. */
class ViewerSessionWebSocketConfigurationTest {

	@Test
	void registersViewerSessionActivityInterceptor() {
		ViewerSessionActivityInterceptor interceptor = mock(
			ViewerSessionActivityInterceptor.class
		);
		ChannelRegistration registration = mock(ChannelRegistration.class);
		ViewerSessionWebSocketConfiguration configuration =
			new ViewerSessionWebSocketConfiguration(interceptor);

		configuration.configureClientInboundChannel(registration);

		verify(registration).interceptors(interceptor);
	}
}
