package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionSettlementDeadlineReader;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** PostgreSQL에서 서버 기동 후 복구할 LIVE 경매 마감 시각을 읽는다. */
@Repository
public class JpaAuctionSettlementDeadlineReader
	implements AuctionSettlementDeadlineReader {

	private static final String FIND_LIVE_DEADLINES = """
		SELECT room.auction_room_id, room.ends_at
		FROM auction_room room
		WHERE room.status = :liveStatus
			AND room.ends_at IS NOT NULL
			AND room.deleted_at IS NULL
		ORDER BY room.ends_at, room.auction_room_id
		""";

	private final EntityManager entityManager;

	public JpaAuctionSettlementDeadlineReader(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public List<Deadline> findLiveDeadlines() {
		List<?> rows = entityManager.createNativeQuery(FIND_LIVE_DEADLINES)
			.setParameter("liveStatus", AuctionStatus.LIVE.name())
			.getResultList();
		return rows.stream()
			.map(Object[].class::cast)
			.map(row -> new Deadline(
				((Number) row[0]).longValue(),
				toInstant(row[1])
			))
			.toList();
	}

	private static Instant toInstant(Object value) {
		return switch (value) {
			case Timestamp timestamp -> timestamp.toInstant();
			case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			case Instant instant -> instant;
			default -> throw new IllegalStateException(
				"Unsupported timestamp type: " + value.getClass().getName()
			);
		};
	}
}
