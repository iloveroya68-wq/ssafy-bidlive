package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.CatchUpRoomAccess;
import com.ssafy.bidlive.auction.application.CatchUpRoomAccessReader;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** PostgreSQL에서 AI-03 접근 판정과 현재 마감 시각에 필요한 값만 읽는다. */
@Repository
public class JpaCatchUpRoomAccessReader implements CatchUpRoomAccessReader {

	private static final String FIND_ACCESS = """
		SELECT
			ar.auction_room_id,
			p.seller_member_id,
			ar.status,
			ar.visibility,
			(ar.deleted_at IS NOT NULL),
			ar.ends_at
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		WHERE ar.auction_room_id = :auctionId
		""";

	private final EntityManager entityManager;

	public JpaCatchUpRoomAccessReader(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<CatchUpRoomAccess> findAccess(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(FIND_ACCESS)
			.setParameter("auctionId", auctionId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new CatchUpRoomAccess(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue(),
			(String) row[2],
			(String) row[3],
			(Boolean) row[4],
			toInstant(row[5])
		));
	}

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
