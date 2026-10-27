package com.ssafy.bidlive.media.infrastructure;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.media.application.ViewerCountService;
import com.ssafy.bidlive.media.application.ViewerSessionTrackingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 현재 추적 중인 경매의 만료 세션 정리와 활성 수 갱신을 주기적으로 실행합니다. */
@Component
public class ViewerSessionCleanupScheduler {

	private final ViewerSessionTrackingService trackingService;
	private final ViewerCountService viewerCountService;

	public ViewerSessionCleanupScheduler(
		ViewerSessionTrackingService trackingService,
		ViewerCountService viewerCountService
	) {
		this.trackingService = Objects.requireNonNull(
			trackingService,
			"trackingService"
		);
		this.viewerCountService = Objects.requireNonNull(
			viewerCountService,
			"viewerCountService"
		);
	}

	@Scheduled(
		fixedDelayString = "${viewer.cleanup-interval-seconds:"
			+ ViewerSessionProperties.DEFAULT_CLEANUP_INTERVAL_SECONDS
			+ "}",
		timeUnit = TimeUnit.SECONDS
	)
	void cleanup() {
		for (Long auctionId : trackingService.trackedAuctionIds()) {
			viewerCountService.refresh(auctionId);
		}
	}
}
