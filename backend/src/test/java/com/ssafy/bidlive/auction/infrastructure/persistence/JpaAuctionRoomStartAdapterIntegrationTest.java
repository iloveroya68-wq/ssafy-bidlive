package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.ssafy.bidlive.auction.application.AuctionRoomStartTarget;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방송 시작 조회·전이 SQL을 실제 PostgreSQL로 검증한다.
 *
 * <p>전이는 `status = 'WAITING'`을 조건에 넣은 UPDATE라 중복 시작을 DB가 막는다 — 두 번째 호출이
 * 0행을 반환하는지가 이 테스트의 핵심이다.
 */
@SpringBootTest
@Transactional
class JpaAuctionRoomStartAdapterIntegrationTest {

	private static final Instant STARTED_AT = Instant.parse("2026-07-21T11:02:13Z");
	private static final Instant END_AT = Instant.parse("2026-07-21T11:17:13Z");

	@Autowired
	private JpaAuctionRoomStartAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (category_id, code, name, is_active, display_order)
			VALUES (9701, 'TEST_START', '시작 테스트', TRUE, 9701)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES (9701, '시작판매자', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (
				product_id, seller_member_id, category_id, title, description, moderation_status
			)
			VALUES
				(9701, 9701, 9701, '시작할 방 상품', '설명', 'PASSED'),
				(9702, 9701, 9701, '차단된 상품', '설명', 'BLOCKED')
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, status, visibility
			)
			VALUES
				(9701, 9701, 50000, 15, 'IMMEDIATE', 'WAITING', 'PUBLIC'),
				(9702, 9702, 50000, 15, 'IMMEDIATE', 'WAITING', 'PRIVATE')
			""");
		entityManager.flush();
	}

	@Test
	void readsSellerStatusVisibilityAndModeration() {
		AuctionRoomStartTarget target = adapter.findStartTarget(9701L).orElseThrow();

		assertThat(target.sellerId()).isEqualTo(9701L);
		assertThat(target.status()).isEqualTo("WAITING");
		assertThat(target.visibility()).isEqualTo("PUBLIC");
		assertThat(target.deleted()).isFalse();
		assertThat(target.reservePrice()).isEqualTo(50_000L);
		assertThat(target.durationMinutes()).isEqualTo(15);
		assertThat(target.moderationStatus()).isEqualTo("PASSED");
	}

	@Test
	void readsBlockedModerationAndPrivateVisibility() {
		AuctionRoomStartTarget target = adapter.findStartTarget(9702L).orElseThrow();

		assertThat(target.moderationStatus()).isEqualTo("BLOCKED");
		assertThat(target.visibility()).isEqualTo("PRIVATE");
	}

	@Test
	void returnsEmptyForMissingRoom() {
		assertThat(adapter.findStartTarget(999_999L)).isEmpty();
	}

	@Test
	void transitionsWaitingRoomToLiveWithTimes() {
		assertThat(adapter.markStarted(9701L, STARTED_AT, END_AT)).isEqualTo(1);
		entityManager.flush();
		entityManager.clear();

		AuctionRoomStartTarget target = adapter.findStartTarget(9701L).orElseThrow();
		assertThat(target.status()).isEqualTo("LIVE");
		assertThat(startedAt(9701L)).isEqualTo(STARTED_AT);
		assertThat(endsAt(9701L)).isEqualTo(END_AT);
	}

	/** 두 번째 시작은 조건에 걸려 0행이다 — 서비스가 이걸로 중복 시작을 판정한다 */
	@Test
	void reportsZeroRowsOnSecondStart() {
		assertThat(adapter.markStarted(9701L, STARTED_AT, END_AT)).isEqualTo(1);
		entityManager.flush();

		assertThat(adapter.markStarted(9701L, STARTED_AT, END_AT)).isZero();
	}

	/** 삭제된 방은 전이 대상이 아니다 */
	@Test
	void refusesToStartDeletedRoom() {
		execute("UPDATE auction_room SET deleted_at = NOW() WHERE auction_room_id = 9701");
		entityManager.flush();

		assertThat(adapter.markStarted(9701L, STARTED_AT, END_AT)).isZero();
	}

	private Instant startedAt(long auctionId) {
		return readInstant("started_at", auctionId);
	}

	private Instant endsAt(long auctionId) {
		return readInstant("ends_at", auctionId);
	}

	private Instant readInstant(String column, long auctionId) {
		Object value = entityManager.createNativeQuery(
				"SELECT " + column + " FROM auction_room WHERE auction_room_id = :id")
			.setParameter("id", auctionId)
			.getSingleResult();
		return switch (value) {
			case java.sql.Timestamp timestamp -> timestamp.toInstant();
			case java.time.OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			case Instant instant -> instant;
			case null -> null;
			default -> throw new IllegalStateException("지원하지 않는 타입: " + value.getClass());
		};
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
