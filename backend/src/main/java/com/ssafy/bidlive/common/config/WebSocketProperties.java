package com.ssafy.bidlive.common.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** BidLive STOMP endpoint와 destination, heartbeat, origin 설정을 제공합니다. */
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

	private String endpoint = "/ws";
	private String applicationDestinationPrefix = "/app";
	private List<String> brokerDestinationPrefixes = List.of("/topic", "/queue");
	private String userDestinationPrefix = "/user";
	private long heartbeatSendInterval = 10_000L;
	private long heartbeatReceiveInterval = 10_000L;
	private List<String> allowedOrigins = List.of("http://localhost:5173");
	private Duration tokenExpiryGrace = Duration.ofSeconds(60);

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = requireDestination(endpoint, "endpoint");
	}

	public String getApplicationDestinationPrefix() {
		return applicationDestinationPrefix;
	}

	public void setApplicationDestinationPrefix(String applicationDestinationPrefix) {
		this.applicationDestinationPrefix = requireDestination(
			applicationDestinationPrefix,
			"applicationDestinationPrefix"
		);
	}

	public List<String> getBrokerDestinationPrefixes() {
		return brokerDestinationPrefixes;
	}

	public void setBrokerDestinationPrefixes(List<String> brokerDestinationPrefixes) {
		this.brokerDestinationPrefixes = requireDestinations(
			brokerDestinationPrefixes,
			"brokerDestinationPrefixes"
		);
	}

	public String getUserDestinationPrefix() {
		return userDestinationPrefix;
	}

	public void setUserDestinationPrefix(String userDestinationPrefix) {
		this.userDestinationPrefix = requireDestination(
			userDestinationPrefix,
			"userDestinationPrefix"
		);
	}

	public long getHeartbeatSendInterval() {
		return heartbeatSendInterval;
	}

	public void setHeartbeatSendInterval(long heartbeatSendInterval) {
		this.heartbeatSendInterval = requirePositive(
			heartbeatSendInterval,
			"heartbeatSendInterval"
		);
	}

	public long getHeartbeatReceiveInterval() {
		return heartbeatReceiveInterval;
	}

	public void setHeartbeatReceiveInterval(long heartbeatReceiveInterval) {
		this.heartbeatReceiveInterval = requirePositive(
			heartbeatReceiveInterval,
			"heartbeatReceiveInterval"
		);
	}

	public List<String> getAllowedOrigins() {
		return allowedOrigins;
	}

	public void setAllowedOrigins(List<String> allowedOrigins) {
		if (allowedOrigins == null || allowedOrigins.isEmpty()) {
			throw new IllegalArgumentException("allowedOrigins must not be empty");
		}
		List<String> normalizedOrigins = allowedOrigins.stream()
			.map(origin -> requireText(origin, "allowedOrigins"))
			.toList();
		if (normalizedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
			throw new IllegalArgumentException("allowedOrigins must not contain a wildcard");
		}
		this.allowedOrigins = List.copyOf(normalizedOrigins);
	}

	public Duration getTokenExpiryGrace() {
		return tokenExpiryGrace;
	}

	/** JWT를 발급한 시계와 이 서버의 시계가 달라도 정상 세션이 끊기지 않도록 두는 유예입니다. */
	public void setTokenExpiryGrace(Duration tokenExpiryGrace) {
		if (tokenExpiryGrace == null || tokenExpiryGrace.isNegative()) {
			throw new IllegalArgumentException(
				"tokenExpiryGrace must not be negative"
			);
		}
		this.tokenExpiryGrace = tokenExpiryGrace;
	}

	private List<String> requireDestinations(List<String> values, String name) {
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException(name + " must not be empty");
		}
		return values.stream()
			.map(value -> requireDestination(value, name))
			.toList();
	}

	private String requireDestination(String value, String name) {
		String destination = requireText(value, name);
		if (!destination.startsWith("/")) {
			throw new IllegalArgumentException(name + " must start with '/'");
		}
		return destination;
	}

	private String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value.trim();
	}

	private long requirePositive(long value, String name) {
		if (value <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}
		return value;
	}
}
