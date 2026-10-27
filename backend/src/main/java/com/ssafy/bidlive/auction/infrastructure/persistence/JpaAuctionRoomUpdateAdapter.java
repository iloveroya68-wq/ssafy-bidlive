package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionRoomUpdatePersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomUpdateTarget;
import com.ssafy.bidlive.auction.domain.AuctionStartType;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 경매방 수정 판정에 필요한 현재 값을 읽고, 확정된 경매 조건을 반영한다. */
@Repository
public class JpaAuctionRoomUpdateAdapter implements AuctionRoomUpdatePersistence {

	private static final String FIND_TARGET = """
		SELECT
			ar.auction_room_id,
			ar.product_id,
			p.seller_member_id,
			ar.status,
			ar.visibility,
			(ar.deleted_at IS NOT NULL),
			ar.reserve_price,
			ar.duration_minutes,
			ar.start_type,
			ar.scheduled_at
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		WHERE ar.auction_room_id = :auctionId
		""";

	private static final String UPDATE_AUCTION_ROOM = """
		UPDATE auction_room
		SET reserve_price = :reservePrice,
			duration_minutes = :durationMinutes,
			start_type = :startType,
			scheduled_at = CAST(:scheduledAt AS TIMESTAMPTZ)
		WHERE auction_room_id = :auctionId
		""";

	/** 상품만 바뀐 요청에서도 최신 시각이 나오도록 두 행의 `updated_at` 중 나중 값을 쓴다. */
	private static final String FIND_LAST_UPDATED_AT = """
		SELECT GREATEST(ar.updated_at, p.updated_at)
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		WHERE ar.auction_room_id = :auctionId
		""";

	private final EntityManager entityManager;

	public JpaAuctionRoomUpdateAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<AuctionRoomUpdateTarget> findUpdateTarget(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(FIND_TARGET)
			.setParameter("auctionId", auctionId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new AuctionRoomUpdateTarget(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue(),
			((Number) row[2]).longValue(),
			(String) row[3],
			(String) row[4],
			(Boolean) row[5],
			((Number) row[6]).longValue(),
			((Number) row[7]).intValue(),
			(String) row[8],
			toInstant(row[9])
		));
	}

	@Override
	public void updateAuctionRoom(
		long auctionId,
		long reservePrice,
		int durationMinutes,
		AuctionStartType startType,
		Instant scheduledAt
	) {
		entityManager.createNativeQuery(UPDATE_AUCTION_ROOM)
			.setParameter("reservePrice", reservePrice)
			.setParameter("durationMinutes", durationMinutes)
			.setParameter("startType", startType.name())
			// 네이티브 파라미터에 null Instant를 그대로 바인딩하면 타입을 못 정하므로 문자열로 넘기고
			// CAST로 시각을 만든다. `IMMEDIATE`는 NULL이어야 한다(`ck_auction_room_schedule`).
			.setParameter("scheduledAt", scheduledAt == null ? null : scheduledAt.toString())
			.setParameter("auctionId", auctionId)
			.executeUpdate();
	}

	@Override
	public Instant findLastUpdatedAt(long auctionId) {
		// 갱신 직후 값을 읽어야 하므로 쓰기를 먼저 DB로 내려보낸다
		entityManager.flush();
		Object value = entityManager.createNativeQuery(FIND_LAST_UPDATED_AT)
			.setParameter("auctionId", auctionId)
			.getSingleResult();
		return toInstant(value);
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
