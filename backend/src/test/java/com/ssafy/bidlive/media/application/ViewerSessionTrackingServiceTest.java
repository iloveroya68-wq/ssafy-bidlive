package com.ssafy.bidlive.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.ssafy.bidlive.media.application.port.out.ViewerSessionStore;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics.RedisOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 구독·heartbeat·해제·다중 탭에 따른 STOMP 세션 생명주기를 검증합니다. */
class ViewerSessionTrackingServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-30T01:00:00Z");

	private ViewerSessionStore sessionStore;
	private ViewerCountService viewerCountService;
	private RealtimeObservationMetrics metrics;
	private ViewerSessionTrackingService service;

	@BeforeEach
	void setUp() {
		sessionStore = mock(ViewerSessionStore.class);
		viewerCountService = mock(ViewerCountService.class);
		metrics = mock(RealtimeObservationMetrics.class);
		service = new ViewerSessionTrackingService(
			sessionStore,
			viewerCountService,
			metrics,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void recordsSessionWhenAuctionTopicIsSubscribed() {
		service.subscribe("session-1", "subscription-1", 42L);

		verify(sessionStore).record(42L, "session-1", NOW);
		verify(viewerCountService).refresh(42L);
	}

	@Test
	void refreshesEachAuctionOnlyOnceOnHeartbeat() {
		service.subscribe("session-1", "chat", 42L);
		service.subscribe("session-1", "viewers", 42L);
		service.subscribe("session-1", "other-auction", 84L);

		service.touch("session-1");

		verify(sessionStore, times(3)).record(42L, "session-1", NOW);
		verify(sessionStore, times(2)).record(84L, "session-1", NOW);
		verify(viewerCountService, times(3)).refresh(42L);
		verify(viewerCountService, times(2)).refresh(84L);
	}

	@Test
	void removesSessionOnlyAfterLastSubscriptionForAuctionIsGone() {
		service.subscribe("session-1", "chat", 42L);
		service.subscribe("session-1", "viewers", 42L);

		service.unsubscribe("session-1", "chat");
		verify(sessionStore, never()).remove(42L, "session-1");

		service.unsubscribe("session-1", "viewers");
		verify(sessionStore).remove(42L, "session-1");
		verify(viewerCountService, times(3)).refresh(42L);
	}

	@Test
	void removesSessionFromEveryAuctionOnDisconnect() {
		service.subscribe("session-1", "chat", 42L);
		service.subscribe("session-1", "viewers", 42L);
		service.subscribe("session-1", "other-auction", 84L);

		service.disconnect("session-1");

		verify(sessionStore).remove(42L, "session-1");
		verify(sessionStore).remove(84L, "session-1");
		verify(viewerCountService, times(3)).refresh(42L);
		verify(viewerCountService, times(2)).refresh(84L);
	}

	@Test
	void tracksMultipleTabsAsIndependentStompSessions() {
		service.subscribe("tab-session-1", "viewers-1", 42L);
		service.subscribe("tab-session-2", "viewers-2", 42L);

		verify(sessionStore).record(42L, "tab-session-1", NOW);
		verify(sessionStore).record(42L, "tab-session-2", NOW);
	}

	@Test
	void refreshesPreviousAndCurrentAuctionWhenSubscriptionDestinationChanges() {
		service.subscribe("session-1", "subscription-1", 42L);

		service.subscribe("session-1", "subscription-1", 84L);

		verify(sessionStore).remove(42L, "session-1");
		verify(sessionStore).record(84L, "session-1", NOW);
		verify(viewerCountService, times(2)).refresh(42L);
		verify(viewerCountService).refresh(84L);
	}

	@Test
	void retriesOnNextActivityAfterRedisRecordFailure() {
		doThrow(new IllegalStateException("redis unavailable"))
			.doNothing()
			.when(sessionStore)
			.record(42L, "session-1", NOW);

		assertThatCode(() ->
			service.subscribe("session-1", "viewers", 42L)
		).doesNotThrowAnyException();
		assertThatCode(() -> service.touch("session-1"))
			.doesNotThrowAnyException();

		verify(sessionStore, times(2)).record(42L, "session-1", NOW);
		verify(viewerCountService).refresh(42L);
		verify(metrics).recordRedisError(RedisOperation.VIEWER_RECORD);
	}

	@Test
	void keepsDisconnectNonBlockingWhenRedisRemoveFails() {
		service.subscribe("session-1", "viewers", 42L);
		clearInvocations(viewerCountService);
		doThrow(new IllegalStateException("redis unavailable"))
			.when(sessionStore)
			.remove(42L, "session-1");

		assertThatCode(() -> service.disconnect("session-1"))
			.doesNotThrowAnyException();
		verify(viewerCountService, never()).refresh(42L);
		verify(metrics).recordRedisError(RedisOperation.VIEWER_REMOVE);
	}

	@Test
	void rejectsBlankSessionIdBeforeTouchingStore() {
		assertThatThrownBy(() ->
			service.subscribe(" ", "viewers", 42L)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("stompSessionId는 비어 있을 수 없습니다.");
	}

	@Test
	void providesDistinctTrackedAuctionIdsForCleanup() {
		service.subscribe("session-1", "chat", 42L);
		service.subscribe("session-1", "viewers", 42L);
		service.subscribe("session-2", "other-auction", 84L);

		assertThat(service.trackedAuctionIds()).containsExactlyInAnyOrder(42L, 84L);

		service.disconnect("session-1");

		assertThat(service.trackedAuctionIds()).containsExactly(84L);
	}
}
