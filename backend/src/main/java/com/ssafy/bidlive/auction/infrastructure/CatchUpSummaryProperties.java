package com.ssafy.bidlive.auction.infrastructure;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI-03의 캐시, 음성 청크와 GMS 모델 정책을 한곳에서 관리한다. */
@ConfigurationProperties(prefix = "app.catch-up-summary")
public class CatchUpSummaryProperties {

	private Duration cacheTtl = Duration.ofMinutes(2);
	private Duration minRefreshInterval = Duration.ofSeconds(30);
	private Duration refreshLockTtl = Duration.ofMinutes(1);
	private Duration sourceTtl = Duration.ofHours(6);
	private int maxAudioChunkBytes = 2 * 1024 * 1024;
	private Set<String> allowedAudioTypes = Set.of(
		"audio/webm",
		"audio/ogg",
		"audio/wav",
		"audio/x-wav"
	);
	private boolean sttEnabled;
	private String transcriptionPath = "/audio/transcriptions";
	private String transcriptionModel = "whisper-1";
	private boolean aiSummaryEnabled = true;
	private String summaryModel = "gpt-4o-mini";

	public Duration getCacheTtl() {
		return cacheTtl;
	}

	public void setCacheTtl(Duration cacheTtl) {
		this.cacheTtl = positive(cacheTtl, "cache-ttl");
	}

	public Duration getMinRefreshInterval() {
		return minRefreshInterval;
	}

	public void setMinRefreshInterval(Duration minRefreshInterval) {
		this.minRefreshInterval = positive(minRefreshInterval, "min-refresh-interval");
	}

	public Duration getRefreshLockTtl() {
		return refreshLockTtl;
	}

	public void setRefreshLockTtl(Duration refreshLockTtl) {
		this.refreshLockTtl = positive(refreshLockTtl, "refresh-lock-ttl");
	}

	public Duration getSourceTtl() {
		return sourceTtl;
	}

	public void setSourceTtl(Duration sourceTtl) {
		this.sourceTtl = positive(sourceTtl, "source-ttl");
	}

	public int getMaxAudioChunkBytes() {
		return maxAudioChunkBytes;
	}

	public void setMaxAudioChunkBytes(int maxAudioChunkBytes) {
		if (maxAudioChunkBytes <= 0) {
			throw new IllegalArgumentException("max-audio-chunk-bytes must be positive");
		}
		this.maxAudioChunkBytes = maxAudioChunkBytes;
	}

	public Set<String> getAllowedAudioTypes() {
		return allowedAudioTypes;
	}

	public void setAllowedAudioTypes(Set<String> allowedAudioTypes) {
		if (allowedAudioTypes == null || allowedAudioTypes.isEmpty()) {
			throw new IllegalArgumentException("allowed-audio-types must not be empty");
		}
		this.allowedAudioTypes = Set.copyOf(allowedAudioTypes);
	}

	public boolean isSttEnabled() {
		return sttEnabled;
	}

	public void setSttEnabled(boolean sttEnabled) {
		this.sttEnabled = sttEnabled;
	}

	public String getTranscriptionPath() {
		return transcriptionPath;
	}

	public void setTranscriptionPath(String transcriptionPath) {
		this.transcriptionPath = text(transcriptionPath, "transcription-path");
	}

	public String getTranscriptionModel() {
		return transcriptionModel;
	}

	public void setTranscriptionModel(String transcriptionModel) {
		this.transcriptionModel = text(transcriptionModel, "transcription-model");
	}

	public boolean isAiSummaryEnabled() {
		return aiSummaryEnabled;
	}

	public void setAiSummaryEnabled(boolean aiSummaryEnabled) {
		this.aiSummaryEnabled = aiSummaryEnabled;
	}

	public String getSummaryModel() {
		return summaryModel;
	}

	public void setSummaryModel(String summaryModel) {
		this.summaryModel = text(summaryModel, "summary-model");
	}

	private static Duration positive(Duration value, String name) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
		return value;
	}

	private static String text(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value.trim();
	}
}
