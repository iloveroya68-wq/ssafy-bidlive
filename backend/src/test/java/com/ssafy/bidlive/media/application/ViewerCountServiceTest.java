package com.ssafy.bidlive.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.ssafy.bidlive.media.application.port.out.ViewerCountPublisher;
import com.ssafy.bidlive.media.application.port.out.ViewerSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TTL 정리 결과가 변경된 경우에만 활성 세션 수가 발행되는지 검증합니다. */
class ViewerCountServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-30T01:00:00Z");
	private static final Duration VIEWER_TTL = Duration.ofSeconds(30);

	private ViewerSessionStore sessionStore;
	private ViewerCountPublisher publisher;
	private ViewerCountService service;

	@BeforeEach
	void setUp() {
		sessionStore = mock(ViewerSessionStore.class);
		publisher = mock(ViewerCountPublisher.class);
		service = new ViewerCountService(
			sessionStore,
			publisher,
			VIEWER_TTL,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void publishesInitialCountAfterRemovingExpiredSessions() {
		when(sessionStore.removeExpiredAndCount(42L, NOW.minus(VIEWER_TTL)))
			.thenReturn(3L);

		service.refresh(42L);

		verify(publisher).publish(new ViewerCountUpdate(
			42L,
			3L,
			OffsetDateTime.parse("2026-07-30T01:00:00Z")
		));
	}

	@Test
	void skipsUnknownInitialZeroCount() {
		when(sessionStore.removeExpiredAndCount(42L, NOW.minus(VIEWER_TTL)))
			.thenReturn(0L);

		service.refresh(42L);

		verify(publisher, never()).publish(any(ViewerCountUpdate.class));
	}

	@Test
	void skipsDuplicateCount() {
		when(sessionStore.removeExpiredAndCount(42L, NOW.minus(VIEWER_TTL)))
			.thenReturn(3L);

		service.refresh(42L);
		service.refresh(42L);

		verify(sessionStore, times(2)).removeExpiredAndCount(
			42L,
			NOW.minus(VIEWER_TTL)
		);
		verify(publisher).publish(any(ViewerCountUpdate.class));
	}

	@Test
	void publishesAgainWhenCountChanges() {
		when(sessionStore.removeExpiredAndCount(42L, NOW.minus(VIEWER_TTL)))
			.thenReturn(3L, 2L);

		service.refresh(42L);
		service.refresh(42L);

		verify(publisher).publish(new ViewerCountUpdate(
			42L,
			3L,
			OffsetDateTime.parse("2026-07-30T01:00:00Z")
		));
		verify(publisher).publish(new ViewerCountUpdate(
			42L,
			2L,
			OffsetDateTime.parse("2026-07-30T01:00:00Z")
		));
	}

	@Test
	void retriesAfterRedisFailureWithoutPublishingZero() {
		when(sessionStore.removeExpiredAndCount(42L, NOW.minus(VIEWER_TTL)))
			.thenThrow(new IllegalStateException("redis unavailable"))
			.thenReturn(4L);

		assertThatCode(() -> service.refresh(42L)).doesNotThrowAnyException();
		verify(publisher, never()).publish(any(ViewerCountUpdate.class));

		service.refresh(42L);

		verify(publisher).publish(new ViewerCountUpdate(
			42L,
			4L,
			OffsetDateTime.parse("2026-07-30T01:00:00Z")
		));
	}

	@Test
	void retriesSameCountWhenPreviousPublishFailed() {
		ViewerCountUpdate update = new ViewerCountUpdate(
			42L,
			3L,
			OffsetDateTime.parse("2026-07-30T01:00:00Z")
		);
		when(sessionStore.removeExpiredAndCount(42L, NOW.minus(VIEWER_TTL)))
			.thenReturn(3L);
		doThrow(new IllegalStateException("broker unavailable"))
			.doNothing()
			.when(publisher)
			.publish(update);

		assertThatCode(() -> service.refresh(42L)).doesNotThrowAnyException();
		service.refresh(42L);

		verify(publisher, times(2)).publish(update);
	}

	@Test
	void serializesConcurrentRefreshesForSameAuction() throws Exception {
		CountDownLatch firstReadStarted = new CountDownLatch(1);
		CountDownLatch allowFirstReadToFinish = new CountDownLatch(1);
		when(sessionStore.removeExpiredAndCount(42L, NOW.minus(VIEWER_TTL)))
			.thenAnswer(invocation -> {
				firstReadStarted.countDown();
				allowFirstReadToFinish.await(5, TimeUnit.SECONDS);
				return 1L;
			})
			.thenReturn(2L);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<?> first = executor.submit(() -> service.refresh(42L));
			assertThat(firstReadStarted.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> second = executor.submit(() -> service.refresh(42L));
			try {
				assertThatThrownBy(() -> second.get(100, TimeUnit.MILLISECONDS))
					.isInstanceOf(TimeoutException.class);
			} finally {
				allowFirstReadToFinish.countDown();
			}

			first.get(5, TimeUnit.SECONDS);
			second.get(5, TimeUnit.SECONDS);
		}

		ArgumentCaptor<ViewerCountUpdate> updates = ArgumentCaptor.forClass(
			ViewerCountUpdate.class
		);
		verify(publisher, times(2)).publish(updates.capture());
		assertThat(updates.getAllValues())
			.extracting(ViewerCountUpdate::viewerCount)
			.containsExactly(1L, 2L);
	}

	@Test
	void rejectsNonPositiveViewerTtl() {
		assertThatThrownBy(() -> new ViewerCountService(
			sessionStore,
			publisher,
			Duration.ZERO
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("viewerTtl은 0보다 커야 합니다.");
	}
}
