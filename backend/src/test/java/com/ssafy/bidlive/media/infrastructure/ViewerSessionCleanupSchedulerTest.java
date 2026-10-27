package com.ssafy.bidlive.media.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.media.application.ViewerCountService;
import com.ssafy.bidlive.media.application.ViewerSessionTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/** 정리 주기마다 현재 추적 중인 경매만 활성 세션 수를 갱신하는지 검증합니다. */
class ViewerSessionCleanupSchedulerTest {

	private ViewerSessionTrackingService trackingService;
	private ViewerCountService viewerCountService;
	private ViewerSessionCleanupScheduler scheduler;

	@BeforeEach
	void setUp() {
		trackingService = mock(ViewerSessionTrackingService.class);
		viewerCountService = mock(ViewerCountService.class);
		scheduler = new ViewerSessionCleanupScheduler(
			trackingService,
			viewerCountService
		);
	}

	@Test
	void refreshesEveryTrackedAuction() {
		when(trackingService.trackedAuctionIds()).thenReturn(Set.of(42L, 84L));

		scheduler.cleanup();

		verify(viewerCountService).refresh(42L);
		verify(viewerCountService).refresh(84L);
	}

	@Test
	void doesNothingWhenNoAuctionIsTracked() {
		when(trackingService.trackedAuctionIds()).thenReturn(Set.of());

		scheduler.cleanup();

		verify(viewerCountService, never()).refresh(
			org.mockito.ArgumentMatchers.anyLong()
		);
	}

	@Test
	void usesConfiguredCleanupIntervalInSeconds() throws NoSuchMethodException {
		Scheduled scheduled = ViewerSessionCleanupScheduler.class
			.getDeclaredMethod("cleanup")
			.getAnnotation(Scheduled.class);

		assertThat(scheduled).isNotNull();
		assertThat(scheduled.fixedDelayString())
			.isEqualTo("${viewer.cleanup-interval-seconds:10}");
		assertThat(scheduled.timeUnit()).isEqualTo(TimeUnit.SECONDS);
	}
}
