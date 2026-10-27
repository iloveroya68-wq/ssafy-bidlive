package com.ssafy.bidlive.chat.infrastructure;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;

public final class ProhibitedWordCacheScheduler {

	private static final long REFRESH_INTERVAL_MINUTES = 5;

	private final InMemoryProhibitedWordProvider provider;

	public ProhibitedWordCacheScheduler(
		InMemoryProhibitedWordProvider provider
	) {
		this.provider = Objects.requireNonNull(provider, "provider");
	}

	@PostConstruct
	void initialize() {
		if (!provider.refresh()) {
			throw new IllegalStateException(
				"금칙어 캐시 최초 적재에 실패했습니다."
			);
		}
	}

	@Scheduled(
		fixedDelay = REFRESH_INTERVAL_MINUTES,
		timeUnit = TimeUnit.MINUTES
	)
	void refresh() {
		provider.refresh();
	}
}
