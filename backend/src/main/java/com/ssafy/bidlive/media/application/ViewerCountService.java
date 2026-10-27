package com.ssafy.bidlive.media.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.ssafy.bidlive.media.application.port.out.ViewerCountPublisher;
import com.ssafy.bidlive.media.application.port.out.ViewerSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 만료 세션을 정리해 활성 수를 집계하고 직전 발행 값과 달라진 경우에만 알립니다. */
public class ViewerCountService {

	private static final Logger log = LoggerFactory.getLogger(ViewerCountService.class);

	private final ViewerSessionStore sessionStore;
	private final ViewerCountPublisher publisher;
	private final Duration viewerTtl;
	private final Clock clock;
	private final ConcurrentMap<Long, Long> lastPublishedCounts =
		new ConcurrentHashMap<>();

	public ViewerCountService(
		ViewerSessionStore sessionStore,
		ViewerCountPublisher publisher,
		Duration viewerTtl
	) {
		this(sessionStore, publisher, viewerTtl, Clock.systemUTC());
	}

	ViewerCountService(
		ViewerSessionStore sessionStore,
		ViewerCountPublisher publisher,
		Duration viewerTtl,
		Clock clock
	) {
		this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
		this.publisher = Objects.requireNonNull(publisher, "publisher");
		this.viewerTtl = requirePositive(viewerTtl);
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public void refresh(long auctionId) {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId는 양수여야 합니다.");
		}

		try {
			lastPublishedCounts.compute(auctionId, (ignored, previousCount) -> {
				Instant measuredInstant = clock.instant();
				long currentCount = sessionStore.removeExpiredAndCount(
					auctionId,
					measuredInstant.minus(viewerTtl)
				);
				if (previousCount == null && currentCount == 0L) {
					return 0L;
				}
				if (previousCount != null && previousCount == currentCount) {
					return previousCount;
				}
				publisher.publish(new ViewerCountUpdate(
					auctionId,
					currentCount,
					OffsetDateTime.ofInstant(measuredInstant, ZoneOffset.UTC)
				));
				return currentCount;
			});
		} catch (RuntimeException exception) {
			log.warn(
				"Viewer count refresh failed: failureType={}",
				exception.getClass().getSimpleName()
			);
		}
	}

	private static Duration requirePositive(Duration viewerTtl) {
		Objects.requireNonNull(viewerTtl, "viewerTtl");
		if (viewerTtl.isZero() || viewerTtl.isNegative()) {
			throw new IllegalArgumentException("viewerTtl은 0보다 커야 합니다.");
		}
		return viewerTtl;
	}
}
