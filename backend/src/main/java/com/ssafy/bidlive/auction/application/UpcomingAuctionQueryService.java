package com.ssafy.bidlive.auction.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.media.application.ViewerCountQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 현재부터 한 시간 이내에 시작하는 공개 대기 경매방 조회를 조율합니다. */
@Service
public class UpcomingAuctionQueryService {

	private static final int DEFAULT_LIMIT = 5;
	private static final int MIN_LIMIT = 1;
	private static final int MAX_LIMIT = 5;
	private static final Duration UPCOMING_WINDOW = Duration.ofHours(1);

	private final UpcomingAuctionPersistence persistence;
	private final ViewerCountQueryService viewerCountQueryService;
	private final Clock clock;

	@Autowired
	public UpcomingAuctionQueryService(
		UpcomingAuctionPersistence persistence,
		ViewerCountQueryService viewerCountQueryService
	) {
		this(persistence, viewerCountQueryService, Clock.systemDefaultZone());
	}

	UpcomingAuctionQueryService(
		UpcomingAuctionPersistence persistence,
		ViewerCountQueryService viewerCountQueryService,
		Clock clock
	) {
		this.persistence = persistence;
		this.viewerCountQueryService = viewerCountQueryService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public UpcomingAuctionResult getUpcoming(Integer limit) {
		Instant now = clock.instant();
		UpcomingAuctionSnapshot snapshot = persistence.findUpcoming(
			now,
			now.plus(UPCOMING_WINDOW),
			clampLimit(limit)
		);
		long hiddenCount = Math.max(
			snapshot.totalCount() - snapshot.rows().size(),
			0L
		);
		return new UpcomingAuctionResult(
			toItems(snapshot.rows()),
			snapshot.totalCount(),
			hiddenCount
		);
	}

	private List<UpcomingAuctionItem> toItems(List<UpcomingAuctionRow> rows) {
		return rows.stream()
			.map(row -> new UpcomingAuctionItem(
				row.auctionId(),
				row.productTitle(),
				row.imageUrl(),
				row.scheduledAt(),
				viewerCountQueryService.activeViewerCount(row.auctionId()),
				row.status()
			))
			.toList();
	}

	private static int clampLimit(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		return Math.clamp(limit, MIN_LIMIT, MAX_LIMIT);
	}
}
