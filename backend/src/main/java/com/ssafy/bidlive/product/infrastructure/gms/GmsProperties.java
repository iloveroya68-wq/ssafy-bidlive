package com.ssafy.bidlive.product.infrastructure.gms;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSAFY GMS(OpenAI 호환 게이트웨이) 접속과 검열 호출 정책 설정입니다.
 *
 * <p>{@code apiKey}는 환경변수·시크릿으로만 주입하며 로그나 예외 메시지에 넣지 않는다.
 * {@code toString}을 재정의하지 않는 이유도 실수로 키가 찍히는 경로를 만들지 않기 위해서다.
 */
@ConfigurationProperties(prefix = "ai.gms")
public class GmsProperties {

	private String baseUrl;
	private String apiKey;

	/** Vision(이미지 입력)을 지원하는 멀티모달 텍스트 모델이어야 한다. */
	private String inspectionModel;

	/** 호출 한 건의 허용 시간이며 초과 시 판정 없이 보류된다. */
	private Duration timeout = Duration.ofSeconds(15);

	/** Credit 절감을 위해 전송 오류에 한해 제한적으로만 재시도한다. */
	private int maxRetries = 1;

	/** 이미지 검열 동시 실행 상한이다. */
	private int maxConcurrentImageInspections = 3;

	/** 한 요청에서 검열할 이미지 개수 상한이며 Credit 소모를 제한한다. */
	private int maxImageCount = 10;

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = requireText(baseUrl, "base-url");
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = requireText(apiKey, "api-key");
	}

	public String getInspectionModel() {
		return inspectionModel;
	}

	public void setInspectionModel(String inspectionModel) {
		this.inspectionModel = requireText(inspectionModel, "inspection-model");
	}

	public Duration getTimeout() {
		return timeout;
	}

	public void setTimeout(Duration timeout) {
		if (timeout == null || timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException("ai.gms.timeout must be positive");
		}
		this.timeout = timeout;
	}

	public int getMaxRetries() {
		return maxRetries;
	}

	public void setMaxRetries(int maxRetries) {
		if (maxRetries < 0) {
			throw new IllegalArgumentException("ai.gms.max-retries must not be negative");
		}
		this.maxRetries = maxRetries;
	}

	public int getMaxConcurrentImageInspections() {
		return maxConcurrentImageInspections;
	}

	public void setMaxConcurrentImageInspections(int maxConcurrentImageInspections) {
		this.maxConcurrentImageInspections =
			requirePositive(maxConcurrentImageInspections, "max-concurrent-image-inspections");
	}

	public int getMaxImageCount() {
		return maxImageCount;
	}

	public void setMaxImageCount(int maxImageCount) {
		this.maxImageCount = requirePositive(maxImageCount, "max-image-count");
	}

	public String bearerAuthorization() {
		return "Bearer " + apiKey;
	}

	private String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("ai.gms." + name + " must not be blank");
		}
		return value.trim();
	}

	private int requirePositive(int value, String name) {
		if (value <= 0) {
			throw new IllegalArgumentException("ai.gms." + name + " must be positive");
		}
		return value;
	}
}
