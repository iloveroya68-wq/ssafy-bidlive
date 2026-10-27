package com.ssafy.bidlive.auction.application;

import java.time.Clock;
import java.util.List;

import com.ssafy.bidlive.auction.domain.AuctionRoomListCursor;
import com.ssafy.bidlive.auction.domain.AuctionRoomListErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.auction.domain.SellerNicknameMask;
import com.ssafy.bidlive.media.application.ViewerCountQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 경매방 목록과 상태별 개수를 조회 조건 검증부터 공개 규칙 적용까지 조율한다. */
@Service
public class AuctionRoomListQueryService {

	private static final int DEFAULT_SIZE = 8;
	private static final int MAX_SIZE = 20;
	private static final int MIN_SIZE = 1;

	private final AuctionRoomListPersistence listPersistence;
	private final ViewerCountQueryService viewerCountQueryService;
	private final Clock clock;

	@Autowired
	public AuctionRoomListQueryService(
		AuctionRoomListPersistence listPersistence,
		ViewerCountQueryService viewerCountQueryService
	) {
		this(listPersistence, viewerCountQueryService, Clock.systemDefaultZone());
	}

	AuctionRoomListQueryService(
		AuctionRoomListPersistence listPersistence,
		ViewerCountQueryService viewerCountQueryService,
		Clock clock
	) {
		this.listPersistence = listPersistence;
		this.viewerCountQueryService = viewerCountQueryService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AuctionRoomListResult getRooms(
		String status,
		Long categoryId,
		String keyword,
		String cursor,
		Integer size
	) {
		AuctionStatus parsedStatus = parseStatus(status);
		AuctionRoomListCursor parsedCursor = parseCursor(cursor);
		verifyCursorMatchesStatus(parsedStatus, parsedCursor);
		AuctionRoomListCriteria criteria = new AuctionRoomListCriteria(
			parsedStatus,
			categoryId,
			normalizeKeyword(keyword),
			parsedCursor,
			clampSize(size)
		);
		if (criteria.categoryId() != null
			&& !listPersistence.existsCategory(criteria.categoryId())) {
			throw new AuctionRoomListException(AuctionRoomListErrorCode.CATEGORY_NOT_FOUND);
		}

		AuctionRoomListSlice slice = listPersistence.findRooms(criteria);
		AuctionRoomStatusCounts counts = listPersistence.countByStatus(criteria);
		return new AuctionRoomListResult(
			clock.instant(),
			toItems(slice.rows()),
			slice.nextCursor(),
			slice.hasNext(),
			counts
		);
	}

	private static AuctionStatus parseStatus(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		try {
			return AuctionStatus.valueOf(status.trim().toUpperCase());
		} catch (IllegalArgumentException exception) {
			throw new AuctionRoomListException(
				AuctionRoomListErrorCode.INVALID_AUCTION_STATUS,
				exception
			);
		}
	}

	private static AuctionRoomListCursor parseCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			return AuctionRoomListCursor.decode(cursor.trim());
		} catch (IllegalArgumentException exception) {
			throw new AuctionRoomListException(
				AuctionRoomListErrorCode.INVALID_CURSOR,
				exception
			);
		}
	}

	/**
	 * 커서는 정렬 기준이 상태별로 다르므로 발급 당시의 상태 필터와 같은 조건에서만 쓸 수 있다.
	 * 탭을 바꾸면서 이전 커서를 그대로 보내면 정렬 키가 맞지 않아 페이지가 조용히 어긋난다 —
	 * `LIVE`의 `ends_at` 값이 `ENDED`의 `ended_at` 내림차순 비교에 쓰이면 방이 그냥 누락된다.
	 */
	private static void verifyCursorMatchesStatus(
		AuctionStatus status,
		AuctionRoomListCursor cursor
	) {
		if (cursor == null) {
			return;
		}
		if (cursor.status() != status) {
			throw new AuctionRoomListException(AuctionRoomListErrorCode.INVALID_CURSOR);
		}
	}

	private static String normalizeKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		return keyword.trim();
	}

	private static int clampSize(Integer size) {
		if (size == null) {
			return DEFAULT_SIZE;
		}
		return Math.clamp(size, MIN_SIZE, MAX_SIZE);
	}

	/**
	 * 접속자 수는 STOMP 세션 기반 실시간 값이라 DB에 없다. 조회 어댑터는 그 자리에 0을 채워
	 * 넘기므로(`JpaAuctionRoomListQueryAdapter`) 여기서 실제 값으로 덮는다 — 그 전까지 목록·홈
	 * 카드의 시청자 수가 **모든 방에서 0으로 고정**이었다.
	 *
	 * <p>상세 조회(`AuctionRoomDetailQueryService`)가 이미 같은 서비스를 쓰고 있다. 목록만
	 * 빠져 있어서 같은 방이 상세에서는 맞고 카드에서는 0인 상태였다.
	 *
	 * <p>끝난 방은 묻지 않는다. 세션 키가 이미 없어 어차피 0이고, 페이지당 최대 20건이 전부
	 * Redis를 왕복할 이유가 없다. (ponytail: 방 하나당 한 번씩 센다. 페이지가 지금보다 훨씬
	 * 커지면 다건 조회로 묶는다.)
	 */
	private List<AuctionRoomSummaryItem> toItems(List<AuctionRoomRow> rows) {
		return rows.stream()
			.map(row -> new AuctionRoomSummaryItem(
				row.auctionId(),
				row.title(),
				row.thumbnailUrl(),
				row.categoryCode(),
				SellerNicknameMask.of(row.sellerNickname()),
				row.reservePrice(),
				row.bidCount(),
				row.bidParticipantCount(),
				row.predictionParticipantCount(),
				row.scheduledAt(),
				row.endAt(),
				row.soldPrice(),
				row.endedAt(),
				row.status(),
				viewerCountOf(row)
			))
			.toList();
	}

	private int viewerCountOf(AuctionRoomRow row) {
		if (row.status() == AuctionStatus.ENDED) {
			return 0;
		}
		return Math.toIntExact(
			viewerCountQueryService.activeViewerCount(row.auctionId())
		);
	}
}
