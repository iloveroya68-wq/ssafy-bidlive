package com.ssafy.bidlive.report.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.bidlive.report.application.AdminReportQueryPersistence;
import com.ssafy.bidlive.report.application.ReportedAuction;
import com.ssafy.bidlive.report.domain.ReportReasonCount;
import com.ssafy.bidlive.report.domain.ReportedAuctionCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/**
 * 신고를 경매별로 집계해 읽는다.
 *
 * <p><b>두 쿼리로 나눈다.</b> 사유별 카운트를 같은 쿼리에 조인하면 경매 행이 사유 수만큼 곱해져
 * {@code reportCount}와 페이지 크기가 함께 틀어진다.
 *
 * <p>커서 조건이 {@code WHERE}가 아니라 {@code HAVING}인 이유: 정렬 키가 집계값
 * {@code MAX(r.created_at)}이라 그룹화 전에는 값이 없다.
 *
 * <p>상태 필터는 {@code <> 'ENDED'}가 아니라 <b>화이트리스트</b>다. {@code auction_room.status}에는
 * DB 제약이 없어, 네 번째 상태가 생기면 블랙리스트는 계약에 없는 방을 조용히 흘린다.
 */
@Repository
public class JpaAdminReportQueryAdapter implements AdminReportQueryPersistence {

	private static final String FIND_REPORTED_AUCTIONS = """
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
			m.nickname,
			ar.status,
			COUNT(*),
			MAX(r.created_at)
		FROM report r
		JOIN auction_room ar ON ar.auction_room_id = r.auction_room_id
		JOIN product p ON p.product_id = ar.product_id
		JOIN member m ON m.member_id = p.seller_member_id
		WHERE r.auction_room_id IS NOT NULL
			AND ar.deleted_at IS NULL
			AND (
					ar.status IN ('WAITING', 'LIVE')
					OR (:includeEnded = TRUE AND ar.status = 'ENDED')
				)
		GROUP BY ar.auction_room_id, p.product_id, p.title, m.nickname, ar.status
		HAVING (
			CAST(:cursorAt AS TIMESTAMPTZ) IS NULL
			OR MAX(r.created_at) < CAST(:cursorAt AS TIMESTAMPTZ)
			OR (
				MAX(r.created_at) = CAST(:cursorAt AS TIMESTAMPTZ)
				AND ar.auction_room_id < :cursorAuctionId
			)
		)
		ORDER BY MAX(r.created_at) DESC, ar.auction_room_id DESC
		LIMIT :limit
		""";

	private static final String FIND_REASON_COUNTS = """
		SELECT
			r.auction_room_id,
			r.reason_code,
			COUNT(*)
		FROM report r
		WHERE r.auction_room_id IN (:auctionIds)
		GROUP BY r.auction_room_id, r.reason_code
		ORDER BY r.auction_room_id, COUNT(*) DESC, r.reason_code ASC
		""";

	private final EntityManager entityManager;

	public JpaAdminReportQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public List<ReportedAuction> findReportedAuctions(
		boolean includeEnded,
		ReportedAuctionCursor cursor,
		int limit
	) {
		Query query = entityManager.createNativeQuery(FIND_REPORTED_AUCTIONS)
			.setParameter("includeEnded", includeEnded)
			.setParameter("cursorAt", cursor == null ? null : cursor.latestReportedAt().toString())
			.setParameter("cursorAuctionId", cursor == null ? 0L : cursor.auctionId())
			.setParameter("limit", limit);
		List<Object[]> rows = query.getResultList();
		return rows.stream()
			.map(row -> new ReportedAuction(
				((Number) row[0]).longValue(),
				(String) row[1],
				(String) row[2],
				(String) row[3],
				(String) row[4],
				((Number) row[5]).intValue(),
				toInstant(row[6]),
				List.of()
			))
			.toList();
	}

	@Override
	public Map<Long, List<ReportReasonCount>> findReasonCounts(List<Long> auctionIds) {
		if (auctionIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<ReportReasonCount>> grouped = new LinkedHashMap<>();
		List<Object[]> rows = entityManager.createNativeQuery(FIND_REASON_COUNTS)
			.setParameter("auctionIds", auctionIds)
			.getResultList();
		rows.forEach(row -> grouped
				.computeIfAbsent(((Number) row[0]).longValue(), key -> new ArrayList<>())
				.add(new ReportReasonCount(
					(String) row[1],
					((Number) row[2]).intValue()
				)));
		return grouped;
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
}
