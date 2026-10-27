package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionRoomJoinPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomJoinTarget;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 대기방 입장 판정에 필요한 경매방 값을 읽는다. */
@Repository
public class JpaAuctionRoomJoinAdapter implements AuctionRoomJoinPersistence {

	private static final String FIND_TARGET = """
		SELECT
			ar.auction_room_id,
			p.seller_member_id,
			ar.status,
			ar.visibility,
			(ar.deleted_at IS NOT NULL),
			ar.scheduled_at
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		WHERE ar.auction_room_id = :auctionId
		""";

	private final EntityManager entityManager;

	public JpaAuctionRoomJoinAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<AuctionRoomJoinTarget> findJoinTarget(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(FIND_TARGET)
			.setParameter("auctionId", auctionId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new AuctionRoomJoinTarget(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue(),
			(String) row[2],
			(String) row[3],
			(Boolean) row[4],
			toInstant(row[5])
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
