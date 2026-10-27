package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.bidlive.auction.application.AuctionRoomListCriteria;
import com.ssafy.bidlive.auction.application.AuctionRoomListPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomListSlice;
import com.ssafy.bidlive.auction.application.AuctionRoomRow;
import com.ssafy.bidlive.auction.application.AuctionRoomStatusCounts;
import com.ssafy.bidlive.auction.domain.AuctionRoomListCursor;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/**
 * 경매방 목록을 상태별 정렬 정책과 키셋 커서로 조회한다. 정렬 키가 같은 방이 있어도 페이지 경계에서
 * 건너뛰거나 중복되지 않도록 (정렬 값, 경매방 ID) 쌍을 커서로 쓴다.
 */
@Repository
public class JpaAuctionRoomListQueryAdapter implements AuctionRoomListPersistence {

	/**
	 * 접속자 수는 STOMP 세션 기반 실시간 값이라 DB에 없다. 집계 저장소(ROOM-07 입장 등록)가 생기기
	 * 전까지는 0으로 반환하며, 프론트는 0을 "표시할 값 없음"으로 그린다.
	 */
	private static final int VIEWER_COUNT_UNAVAILABLE = 0;

	/** `auction_room.status`에는 DB 제약이 없어 공개 목록에 쓰는 3개 상태만 받는다. */
	private static final String PUBLIC_STATUSES = "ar.status IN ('WAITING', 'LIVE', 'ENDED')";
	private static final String PUBLIC_ROOM =
		"ar.visibility = 'PUBLIC' AND ar.deleted_at IS NULL";

	/**
	 * 검열로 차단된 상품은 공개 목록에서 제외한다.
	 *
	 * <p>`PASSED`만 통과시키지 **않는다** — 등록 직후 상태가 `PENDING`이라 그렇게 하면 검수 전
	 * 방이 목록에서 통째로 사라진다. `FLAGGED`는 AI 미확정이라 오탐인 정상 방을 지우지 않는다.
	 * 차단이 확정된 `BLOCKED`만 뺀다.
	 */
	private static final String NOT_BLOCKED_PRODUCT = "p.moderation_status <> 'BLOCKED'";

	/**
	 * 입찰 집계는 두 축이 다르다. `bidCount`는 `bid` 행 수(누적 입찰 건수)이고
	 * `bidParticipantCount`는 입찰한 고유 회원 수다. `auction_participant`가
	 * `(auction_room_id, member_id)` 유일 제약을 가지므로 `DISTINCT member_id`가 곧 참여 인원이며,
	 * 다중·상향 입찰은 한 명으로 집계된다 (WS-03의 같은 필드 정의와 일치).
	 *
	 * <p>둘을 하나로 합치지 않는 이유는 카드의 「참여 N명」이 건수가 아니라 인원이기 때문이다.
	 * 의미가 다른 값을 매핑하면 화면은 멀쩡해 보이지만 숫자가 틀린다.
	 */
	private static final String SELECT_ROOMS = """
		SELECT
			ar.auction_room_id,
			p.title,
			(
				SELECT thumbnail.image_url
				FROM product_image thumbnail
				WHERE thumbnail.product_id = p.product_id
					AND thumbnail.moderation_status <> 'BLOCKED'
				ORDER BY thumbnail.is_thumbnail DESC, thumbnail.sort_order ASC
				LIMIT 1
			),
			pc.code,
			m.nickname,
			ar.reserve_price,
			(
				SELECT COUNT(*)
				FROM bid b
				JOIN auction_participant bidder
					ON bidder.auction_participant_id = b.auction_participant_id
				WHERE bidder.auction_room_id = ar.auction_room_id
			),
			(
				SELECT COUNT(DISTINCT bidder.member_id)
				FROM bid b
				JOIN auction_participant bidder
					ON bidder.auction_participant_id = b.auction_participant_id
				WHERE bidder.auction_room_id = ar.auction_room_id
			),
			(
				SELECT COUNT(*)
				FROM prediction pr
				JOIN auction_participant predictor
					ON predictor.auction_participant_id = pr.auction_participant_id
				WHERE predictor.auction_room_id = ar.auction_room_id
			),
			ar.scheduled_at,
			ar.ends_at,
			sold.final_price,
			ar.ended_at,
			ar.status
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		JOIN product_category pc ON pc.category_id = p.category_id
		JOIN member m ON m.member_id = p.seller_member_id
		LEFT JOIN auction_result sold
			ON sold.auction_room_id = ar.auction_room_id
			AND sold.result_status = 'SOLD'
		""";

	private static final String COUNT_BY_STATUS = """
		SELECT
			COUNT(*) FILTER (WHERE ar.status = 'LIVE'),
			COUNT(*) FILTER (WHERE ar.status = 'WAITING')
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		""";

	private static final String EXISTS_CATEGORY = """
		SELECT EXISTS (
			SELECT 1 FROM product_category WHERE category_id = :categoryId
		)
		""";

	private final EntityManager entityManager;

	public JpaAuctionRoomListQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public AuctionRoomListSlice findRooms(AuctionRoomListCriteria criteria) {
		SortPlan sortPlan = SortPlan.of(criteria.status());
		Map<String, Object> parameters = new LinkedHashMap<>();
		List<String> conditions = new ArrayList<>();
		conditions.add(PUBLIC_STATUSES);
		conditions.add(PUBLIC_ROOM);
		conditions.add(NOT_BLOCKED_PRODUCT);
		addScopeConditions(criteria, conditions, parameters);
		if (criteria.status() != null) {
			conditions.add("ar.status = :status");
			parameters.put("status", criteria.status().name());
		}
		if (criteria.cursor() != null) {
			conditions.add(sortPlan.cursorCondition());
			parameters.put("cursorId", criteria.cursor().auctionId());
			if (sortPlan.sortColumn() != null) {
				parameters.put("cursorSortValue", criteria.cursor().sortValue());
			}
		}

		Query query = entityManager.createNativeQuery(
			SELECT_ROOMS + where(conditions) + sortPlan.orderBy()
		);
		parameters.forEach(query::setParameter);
		query.setMaxResults(criteria.size() + 1);

		List<AuctionRoomRow> rows = new ArrayList<>(query.getResultList().stream()
			.map(JpaAuctionRoomListQueryAdapter::toRow)
			.toList());
		boolean hasNext = rows.size() > criteria.size();
		if (hasNext) {
			rows = rows.subList(0, criteria.size());
		}
		String nextCursor = hasNext
			? sortPlan.cursorOf(rows.get(rows.size() - 1)).encode()
			: null;
		return new AuctionRoomListSlice(rows, nextCursor, hasNext);
	}

	@Override
	public AuctionRoomStatusCounts countByStatus(AuctionRoomListCriteria criteria) {
		Map<String, Object> parameters = new LinkedHashMap<>();
		List<String> conditions = new ArrayList<>();
		conditions.add(PUBLIC_STATUSES);
		conditions.add(PUBLIC_ROOM);
		conditions.add(NOT_BLOCKED_PRODUCT);
		addScopeConditions(criteria, conditions, parameters);

		Query query = entityManager.createNativeQuery(COUNT_BY_STATUS + where(conditions));
		parameters.forEach(query::setParameter);

		Object[] row = (Object[]) query.getSingleResult();
		return new AuctionRoomStatusCounts(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue()
		);
	}

	@Override
	public boolean existsCategory(long categoryId) {
		return (Boolean) entityManager.createNativeQuery(EXISTS_CATEGORY)
			.setParameter("categoryId", categoryId)
			.getSingleResult();
	}

	/** 목록과 개수 집계에 공통으로 적용되는 조건이다 (상태 필터·커서는 목록에만 적용된다). */
	private static void addScopeConditions(
		AuctionRoomListCriteria criteria,
		List<String> conditions,
		Map<String, Object> parameters
	) {
		if (criteria.categoryId() != null) {
			conditions.add("p.category_id = :categoryId");
			parameters.put("categoryId", criteria.categoryId());
		}
		if (criteria.keyword() != null) {
			conditions.add("p.title ILIKE :keyword");
			parameters.put("keyword", toLikePattern(criteria.keyword()));
		}
	}

	private static String where(List<String> conditions) {
		return "WHERE " + String.join("\n\tAND ", conditions) + "\n";
	}

	/** 사용자 입력의 `%`·`_`가 와일드카드로 동작하지 않게 막는다 (PostgreSQL 기본 escape는 백슬래시). */
	private static String toLikePattern(String keyword) {
		String escaped = keyword
			.replace("\\", "\\\\")
			.replace("%", "\\%")
			.replace("_", "\\_");
		return "%" + escaped + "%";
	}

	private static AuctionRoomRow toRow(Object value) {
		Object[] row = (Object[]) value;
		return new AuctionRoomRow(
			((Number) row[0]).longValue(),
			(String) row[1],
			(String) row[2],
			(String) row[3],
			(String) row[4],
			((Number) row[5]).longValue(),
			((Number) row[6]).longValue(),
			((Number) row[7]).longValue(),
			((Number) row[8]).longValue(),
			toInstant(row[9]),
			toInstant(row[10]),
			row[11] == null ? null : ((Number) row[11]).longValue(),
			toInstant(row[12]),
			AuctionStatus.valueOf((String) row[13]),
			VIEWER_COUNT_UNAVAILABLE
		);
	}

	/** 드라이버·Hibernate 조합에 따라 `TIMESTAMPTZ`가 여러 타입으로 올 수 있어 한곳에서 흡수한다. */
	private static Instant toInstant(Object value) {
		return switch (value) {
			case null -> null;
			case Instant instant -> instant;
			case Timestamp timestamp -> timestamp.toInstant();
			case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			default -> throw new IllegalStateException(
				"지원하지 않는 시각 타입: " + value.getClass()
			);
		};
	}

	/**
	 * 상태별 정렬 정책이다 — `LIVE`는 마감 임박순, `WAITING`은 시작 예정순, `ENDED`는 종료 최신순.
	 * 정렬 기준 시각이 없는 방은 `infinity` 센티넬로 항상 뒤로 밀어 커서 비교에서도 순서가 유지된다.
	 */
	private record SortPlan(
		AuctionStatus status,
		String sortColumn,
		boolean ascending,
		String nullSentinel
	) {

		static SortPlan of(AuctionStatus status) {
			if (status == null) {
				// 명세가 상태 필터 없는 목록의 정렬을 정하지 않았다 — 커서가 흔들리지 않는 ID 역순으로 둔다
				return new SortPlan(null, null, false, null);
			}
			// 센티넬은 커서와 같은 문자열을 써야 한다 — 갈리면 정렬과 커서 비교가 어긋난다
			return switch (status) {
				case LIVE -> new SortPlan(
					status, "ar.ends_at", true, AuctionRoomListCursor.LAST_SENTINEL
				);
				case WAITING -> new SortPlan(
					status, "ar.scheduled_at", true, AuctionRoomListCursor.LAST_SENTINEL
				);
				case ENDED -> new SortPlan(
					status, "ar.ended_at", false, AuctionRoomListCursor.LAST_SENTINEL_DESC
				);
			};
		}

		String orderBy() {
			String direction = ascending ? "ASC" : "DESC";
			if (sortColumn == null) {
				return "ORDER BY ar.auction_room_id " + direction;
			}
			return "ORDER BY " + sortExpression() + " " + direction
				+ ", ar.auction_room_id " + direction;
		}

		String cursorCondition() {
			String comparison = ascending ? ">" : "<";
			if (sortColumn == null) {
				return "ar.auction_room_id " + comparison + " :cursorId";
			}
			return "(" + sortExpression() + ", ar.auction_room_id) " + comparison
				+ " (CAST(:cursorSortValue AS TIMESTAMPTZ), :cursorId)";
		}

		AuctionRoomListCursor cursorOf(AuctionRoomRow row) {
			if (sortColumn == null) {
				return new AuctionRoomListCursor(null, null, row.auctionId());
			}
			Instant sortedAt = sortedAt(row);
			return new AuctionRoomListCursor(
				status,
				sortedAt == null ? nullSentinel : sortedAt.toString(),
				row.auctionId()
			);
		}

		private Instant sortedAt(AuctionRoomRow row) {
			return switch (sortColumn) {
				case "ar.ends_at" -> row.endAt();
				case "ar.scheduled_at" -> row.scheduledAt();
				default -> row.endedAt();
			};
		}

		private String sortExpression() {
			return "COALESCE(" + sortColumn + ", '" + nullSentinel + "'::TIMESTAMPTZ)";
		}
	}
}
