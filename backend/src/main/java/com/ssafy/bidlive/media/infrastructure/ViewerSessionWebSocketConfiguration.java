package com.ssafy.bidlive.media.infrastructure;

import com.ssafy.bidlive.media.api.ViewerSessionActivityInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** media 시청 세션 활동 인터셉터를 공통 STOMP inbound channel에 추가합니다. */
@Configuration(proxyBeanMethods = false)
@Order(Ordered.LOWEST_PRECEDENCE)
public class ViewerSessionWebSocketConfiguration
	implements WebSocketMessageBrokerConfigurer {

	private final ViewerSessionActivityInterceptor activityInterceptor;

	public ViewerSessionWebSocketConfiguration(
		ViewerSessionActivityInterceptor activityInterceptor
	) {
		this.activityInterceptor = activityInterceptor;
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(activityInterceptor);
	}
}
