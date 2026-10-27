package com.ssafy.bidlive.common.config;

import com.ssafy.bidlive.common.api.StompTokenRenewalController;
import com.ssafy.bidlive.common.security.StompConnectAuthenticationInterceptor;
import com.ssafy.bidlive.common.security.StompConnectErrorHandler;
import com.ssafy.bidlive.common.observability.StompCorrelationInterceptor;
import com.ssafy.bidlive.common.websocket.StompErrorContextResolver;
import com.ssafy.bidlive.common.websocket.UserWebSocketErrorPublisher;
import com.ssafy.bidlive.common.websocket.expiry.StompSessionExpiryInterceptor;
import com.ssafy.bidlive.common.websocket.send.StompSendDestinationInterceptor;
import com.ssafy.bidlive.common.websocket.subscription.StompSubscriptionAuthorizationInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** BidLive STOMP endpoint, broker destination, heartbeat와 CONNECT 인증을 구성합니다. */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final WebSocketProperties properties;
	private final StompCorrelationInterceptor correlationInterceptor;
	private final StompConnectAuthenticationInterceptor authenticationInterceptor;
	private final StompSessionExpiryInterceptor sessionExpiryInterceptor;
	private final StompSubscriptionAuthorizationInterceptor subscriptionInterceptor;
	private final StompSendDestinationInterceptor sendDestinationInterceptor;
	private final StompConnectErrorHandler connectErrorHandler;

	public WebSocketConfig(
		WebSocketProperties properties,
		StompCorrelationInterceptor correlationInterceptor,
		StompConnectAuthenticationInterceptor authenticationInterceptor,
		StompSubscriptionAuthorizationInterceptor subscriptionInterceptor,
		StompErrorContextResolver errorContextResolver,
		@Lazy UserWebSocketErrorPublisher errorPublisher,
		StompConnectErrorHandler connectErrorHandler
	) {
		this.properties = properties;
		this.correlationInterceptor = correlationInterceptor;
		this.authenticationInterceptor = authenticationInterceptor;
		this.subscriptionInterceptor = subscriptionInterceptor;
		this.sessionExpiryInterceptor = new StompSessionExpiryInterceptor(
			properties,
			StompTokenRenewalController.RENEW_MAPPING,
			errorContextResolver,
			errorPublisher
		);
		this.sendDestinationInterceptor = new StompSendDestinationInterceptor(
			properties,
			errorContextResolver,
			errorPublisher
		);
		this.connectErrorHandler = connectErrorHandler;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.setErrorHandler(connectErrorHandler);
		registry.addEndpoint(properties.getEndpoint())
			.setAllowedOrigins(properties.getAllowedOrigins().toArray(String[]::new));
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.setApplicationDestinationPrefixes(properties.getApplicationDestinationPrefix());
		registry.setUserDestinationPrefix(properties.getUserDestinationPrefix());
		registry.enableSimpleBroker(
			properties.getBrokerDestinationPrefixes().toArray(String[]::new)
		)
			.setHeartbeatValue(new long[] {
				properties.getHeartbeatSendInterval(),
				properties.getHeartbeatReceiveInterval()
			})
			.setTaskScheduler(webSocketHeartbeatTaskScheduler());
	}

	/**
	 * 세션 만료 검사는 CONNECT 인증 뒤, destination별 인가 앞에 둡니다. 만료된 세션의 구독·전송은
	 * 권한이 아니라 인증이 끊긴 것이라 {@code SUBSCRIPTION_DENIED}·{@code SEND_DENIED}로 알리면
	 * 클라이언트가 재인증할 이유를 알 수 없습니다.
	 */
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(
			correlationInterceptor,
			authenticationInterceptor,
			sessionExpiryInterceptor,
			subscriptionInterceptor,
			sendDestinationInterceptor
		);
	}

	@Bean
	TaskScheduler webSocketHeartbeatTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("ws-heartbeat-");
		scheduler.setRemoveOnCancelPolicy(true);
		return scheduler;
	}
}
