package com.ssafy.bidlive.chat.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 비동기 채팅 검열의 외부 연결·worker·retry 정책 설정을 보관합니다. */
@ConfigurationProperties(prefix = "chat.moderation")
@Component
public class ChatAsyncModerationProperties {

	private boolean enabled;
	private String baseUrl;
	private String apiKey;
	private String model = "gpt-4o-mini";
	private Duration timeout = Duration.ofSeconds(5);
	private Duration pollDelay = Duration.ofSeconds(1);
	private Duration baseBackoff = Duration.ofSeconds(2);
	private Duration lease = Duration.ofMinutes(1);
	private int maxAttempts = 3;
	private int batchSize = 10;
	private int violationThreshold = 3;
	private Duration violationWindow = Duration.ofMinutes(30);
	private Duration muteDuration = Duration.ofMinutes(10);

	public void validateEnabled() {
		if (!enabled) {
			return;
		}
		requireText(baseUrl, "base-url");
		requireText(apiKey, "api-key");
		requireText(model, "model");
		requirePositive(timeout, "timeout");
		requirePositive(pollDelay, "poll-delay");
		requirePositive(baseBackoff, "base-backoff");
		requirePositive(lease, "lease");
		if (maxAttempts <= 0 || batchSize <= 0) {
			throw new IllegalArgumentException(
				"max-attempts and batch-size must be positive"
			);
		}
		if (violationThreshold <= 0) {
			throw new IllegalArgumentException(
				"violation-threshold must be positive"
			);
		}
		requirePositive(violationWindow, "violation-window");
		requirePositive(muteDuration, "mute-duration");
	}

	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public String getBaseUrl() { return baseUrl; }
	public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
	public String getApiKey() { return apiKey; }
	public void setApiKey(String apiKey) { this.apiKey = apiKey; }
	public String getModel() { return model; }
	public void setModel(String model) { this.model = model; }
	public Duration getTimeout() { return timeout; }
	public void setTimeout(Duration timeout) { this.timeout = timeout; }
	public Duration getPollDelay() { return pollDelay; }
	public void setPollDelay(Duration pollDelay) { this.pollDelay = pollDelay; }
	public Duration getBaseBackoff() { return baseBackoff; }
	public void setBaseBackoff(Duration baseBackoff) {
		this.baseBackoff = baseBackoff;
	}
	public Duration getLease() { return lease; }
	public void setLease(Duration lease) { this.lease = lease; }
	public int getMaxAttempts() { return maxAttempts; }
	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}
	public int getBatchSize() { return batchSize; }
	public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
	public int getViolationThreshold() { return violationThreshold; }
	public void setViolationThreshold(int violationThreshold) {
		this.violationThreshold = violationThreshold;
	}
	public Duration getViolationWindow() { return violationWindow; }
	public void setViolationWindow(Duration violationWindow) {
		this.violationWindow = violationWindow;
	}
	public Duration getMuteDuration() { return muteDuration; }
	public void setMuteDuration(Duration muteDuration) {
		this.muteDuration = muteDuration;
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
				"chat.moderation." + name + " must not be blank"
			);
		}
	}

	private static void requirePositive(Duration value, String name) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException(
				"chat.moderation." + name + " must be positive"
			);
		}
	}
}
