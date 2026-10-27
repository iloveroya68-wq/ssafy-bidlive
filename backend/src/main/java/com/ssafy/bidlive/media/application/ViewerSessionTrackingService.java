package com.ssafy.bidlive.media.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.ssafy.bidlive.media.application.port.out.ViewerSessionStore;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics.RedisOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** STOMP 세션별 경매 구독을 추적하고 활동 시각과 활성 세션 수를 갱신합니다. */
@Service
public class ViewerSessionTrackingService {

	private static final Logger log = LoggerFactory.getLogger(
		ViewerSessionTrackingService.class
	);

	private final ViewerSessionStore sessionStore;
	private final ViewerCountService viewerCountService;
	private final RealtimeObservationMetrics metrics;
	private final Clock clock;
	private final ConcurrentMap<String, SessionSubscriptions> sessions =
		new ConcurrentHashMap<>();

	@Autowired
	public ViewerSessionTrackingService(
		ViewerSessionStore sessionStore,
		@Lazy ViewerCountService viewerCountService,
		RealtimeObservationMetrics metrics
	) {
		this(
			sessionStore,
			viewerCountService,
			metrics,
			Clock.systemUTC()
		);
	}

	public ViewerSessionTrackingService(
		ViewerSessionStore sessionStore,
		ViewerCountService viewerCountService
	) {
		this(sessionStore, viewerCountService, null, Clock.systemUTC());
	}

	ViewerSessionTrackingService(
		ViewerSessionStore sessionStore,
		ViewerCountService viewerCountService,
		Clock clock
	) {
		this(sessionStore, viewerCountService, null, clock);
	}

	ViewerSessionTrackingService(
		ViewerSessionStore sessionStore,
		ViewerCountService viewerCountService,
		RealtimeObservationMetrics metrics,
		Clock clock
	) {
		this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
		this.viewerCountService = Objects.requireNonNull(
			viewerCountService,
			"viewerCountService"
		);
		this.metrics = metrics;
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public void subscribe(
		String stompSessionId,
		String subscriptionId,
		long auctionId
	) {
		requireText(stompSessionId, "stompSessionId");
		requireText(subscriptionId, "subscriptionId");
		requirePositive(auctionId);
		AtomicReference<Long> removedAuctionId = new AtomicReference<>();
		AtomicBoolean recorded = new AtomicBoolean();

		sessions.compute(stompSessionId, (ignored, existing) -> {
			SessionSubscriptions subscriptions = existing == null
				? new SessionSubscriptions()
				: existing;
			Long previousAuctionId = subscriptions.put(
				subscriptionId,
				auctionId
			);
			if (previousAuctionId != null
				&& previousAuctionId != auctionId
				&& !subscriptions.contains(previousAuctionId)
				&& safeRemove(previousAuctionId, stompSessionId)) {
				removedAuctionId.set(previousAuctionId);
			}
			recorded.set(safeRecord(
				auctionId,
				stompSessionId,
				clock.instant()
			));
			return subscriptions;
		});

		if (removedAuctionId.get() != null) {
			viewerCountService.refresh(removedAuctionId.get());
		}
		if (recorded.get()) {
			viewerCountService.refresh(auctionId);
		}
	}

	public void touch(String stompSessionId) {
		requireText(stompSessionId, "stompSessionId");
		Set<Long> recordedAuctionIds = new HashSet<>();

		sessions.computeIfPresent(stompSessionId, (ignored, subscriptions) -> {
			Instant lastSeenAt = clock.instant();
			for (Long auctionId : subscriptions.auctionIds()) {
				if (safeRecord(auctionId, stompSessionId, lastSeenAt)) {
					recordedAuctionIds.add(auctionId);
				}
			}
			return subscriptions;
		});

		for (Long auctionId : recordedAuctionIds) {
			viewerCountService.refresh(auctionId);
		}
	}

	public void unsubscribe(
		String stompSessionId,
		String subscriptionId
	) {
		requireText(stompSessionId, "stompSessionId");
		requireText(subscriptionId, "subscriptionId");
		AtomicReference<Long> auctionIdToRefresh = new AtomicReference<>();

		sessions.computeIfPresent(stompSessionId, (ignored, subscriptions) -> {
			Long unsubscribedAuctionId = subscriptions.remove(subscriptionId);
			if (unsubscribedAuctionId != null
				&& !subscriptions.contains(unsubscribedAuctionId)
				&& safeRemove(unsubscribedAuctionId, stompSessionId)) {
				auctionIdToRefresh.set(unsubscribedAuctionId);
			}
			return subscriptions.isEmpty() ? null : subscriptions;
		});

		if (auctionIdToRefresh.get() != null) {
			viewerCountService.refresh(auctionIdToRefresh.get());
		}
	}

	public void disconnect(String stompSessionId) {
		requireText(stompSessionId, "stompSessionId");
		Set<Long> removedAuctionIds = new HashSet<>();

		sessions.computeIfPresent(stompSessionId, (ignored, subscriptions) -> {
			for (Long auctionId : subscriptions.auctionIds()) {
				if (safeRemove(auctionId, stompSessionId)) {
					removedAuctionIds.add(auctionId);
				}
			}
			return null;
		});

		for (Long auctionId : removedAuctionIds) {
			viewerCountService.refresh(auctionId);
		}
	}

	public Set<Long> trackedAuctionIds() {
		Set<Long> auctionIds = new HashSet<>();
		for (SessionSubscriptions subscriptions : sessions.values()) {
			auctionIds.addAll(subscriptions.auctionIds());
		}
		return Set.copyOf(auctionIds);
	}

	private boolean safeRecord(
		long auctionId,
		String stompSessionId,
		Instant lastSeenAt
	) {
		try {
			sessionStore.record(auctionId, stompSessionId, lastSeenAt);
			return true;
		} catch (RuntimeException exception) {
			recordRedisError(RedisOperation.VIEWER_RECORD);
			log.warn(
				"Viewer session Redis operation failed: operation=record, failureType={}",
				exception.getClass().getSimpleName()
			);
			return false;
		}
	}

	private boolean safeRemove(long auctionId, String stompSessionId) {
		try {
			sessionStore.remove(auctionId, stompSessionId);
			return true;
		} catch (RuntimeException exception) {
			recordRedisError(RedisOperation.VIEWER_REMOVE);
			log.warn(
				"Viewer session Redis operation failed: operation=remove, failureType={}",
				exception.getClass().getSimpleName()
			);
			return false;
		}
	}

	private void recordRedisError(RedisOperation operation) {
		if (metrics != null) {
			metrics.recordRedisError(operation);
		}
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + "는 비어 있을 수 없습니다.");
		}
	}

	private static void requirePositive(long auctionId) {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId는 양수여야 합니다.");
		}
	}

	/** 단일 STOMP 세션의 구독 ID와 경매 ID 관계를 동기화하여 관리합니다. */
	private static final class SessionSubscriptions {

		private final Map<String, Long> auctionIdsBySubscription =
			new HashMap<>();

		synchronized Long put(String subscriptionId, long auctionId) {
			return auctionIdsBySubscription.put(subscriptionId, auctionId);
		}

		synchronized Long remove(String subscriptionId) {
			return auctionIdsBySubscription.remove(subscriptionId);
		}

		synchronized boolean contains(long auctionId) {
			return auctionIdsBySubscription.containsValue(auctionId);
		}

		synchronized Set<Long> auctionIds() {
			return new HashSet<>(auctionIdsBySubscription.values());
		}

		synchronized boolean isEmpty() {
			return auctionIdsBySubscription.isEmpty();
		}
	}
}
