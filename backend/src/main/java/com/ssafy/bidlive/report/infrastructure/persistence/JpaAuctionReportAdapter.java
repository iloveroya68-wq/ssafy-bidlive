package com.ssafy.bidlive.report.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.report.application.AuctionReportCommand;
import com.ssafy.bidlive.report.application.AuctionReportPersistence;
import com.ssafy.bidlive.report.application.AuctionReportResult;
import com.ssafy.bidlive.report.application.AuctionReportTarget;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 신고 판정에 필요한 사실을 한 번에 읽고 신고 행을 남긴다. */
@Repository
public class JpaAuctionReportAdapter implements AuctionReportPersistence {

	/** 경매방 신고임을 나타내는 `report.target_type`. 이 값이면 `auction_room_id`만 채운다. */
	private static final String TARGET_TYPE_AUCTION_ROOM = "AUCTION_ROOM";

	/** 접수 직후 상태. 운영자 검토 전까지 이 값이다. */
	private static final String STATUS_RECEIVED = "RECEIVED";

	private static final String FIND_TARGET = """
		SELECT
			ar.auction_room_id,
			p.seller_member_id,
			ar.status,
			ar.visibility,
			(ar.deleted_at IS NOT NULL)
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		WHERE ar.auction_room_id = :auctionId
		""";

	private static final String EXISTS_REPORT = """
		SELECT EXISTS (
			SELECT 1
			FROM report
			WHERE auction_room_id = :auctionId
				AND reporter_member_id = :reporterId
		)
		""";

	private static final String INSERT_REPORT = """
		INSERT INTO report (
			reporter_member_id,
			target_type,
			auction_room_id,
			reason_code,
			description,
			status,
			created_at
		)
		VALUES (
			:reporterId,
			:targetType,
			:auctionId,
			:reasonCode,
			NULLIF(:description, ''),
			:status,
			:receivedAt
		)
		ON CONFLICT (auction_room_id, reporter_member_id)
			WHERE auction_room_id IS NOT NULL
			DO NOTHING
		RETURNING report_id, created_at
		""";

	private final EntityManager entityManager;

	public JpaAuctionReportAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<AuctionReportTarget> findReportTarget(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(FIND_TARGET)
			.setParameter("auctionId", auctionId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new AuctionReportTarget(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue(),
			(String) row[2],
			(String) row[3],
			(Boolean) row[4]
		));
	}

	@Override
	public boolean existsReport(long auctionId, long reporterMemberId) {
		return (Boolean) entityManager.createNativeQuery(EXISTS_REPORT)
			.setParameter("auctionId", auctionId)
			.setParameter("reporterId", reporterMemberId)
			.getSingleResult();
	}

	@Override
	public Optional<AuctionReportResult> save(
		long auctionId,
		long reporterMemberId,
		AuctionReportCommand command,
		Instant receivedAt
	) {
		List<?> rows = entityManager.createNativeQuery(INSERT_REPORT)
			.setParameter("reporterId", reporterMemberId)
			.setParameter("targetType", TARGET_TYPE_AUCTION_ROOM)
			.setParameter("auctionId", auctionId)
			.setParameter("reasonCode", command.reason().name())
			// 네이티브 쿼리에 null을 그대로 바인딩하면 타입을 못 정해 터진다 — 빈 문자열로 넘기고
			// SQL의 NULLIF가 NULL로 눕힌다
			.setParameter("description", command.description() == null ? "" : command.description())
			.setParameter("status", STATUS_RECEIVED)
			.setParameter("receivedAt", receivedAt)
			.getResultList();
		if (rows.isEmpty()) {
			// 유니크 인덱스가 잡았다 — 앱 EXISTS를 통과한 동시 요청이 여기서 걸린다
			return Optional.empty();
		}

		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new AuctionReportResult(
			((Number) row[0]).longValue(),
			STATUS_RECEIVED,
			toInstant(row[1])
		));
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
