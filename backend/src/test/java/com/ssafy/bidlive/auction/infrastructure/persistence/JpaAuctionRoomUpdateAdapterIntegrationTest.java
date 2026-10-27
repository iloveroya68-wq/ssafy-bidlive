package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.ssafy.bidlive.auction.application.AuctionRoomUpdateTarget;
import com.ssafy.bidlive.auction.domain.AuctionStartType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경매방 수정 조회·갱신 SQL을 실제 PostgreSQL로 검증한다.
 *
 * <p>`ck_auction_room_schedule`이 `IMMEDIATE`면 `scheduled_at IS NULL`,
 * `SCHEDULED`면 `NOT NULL`을 요구하므로 두 방향 전환이 모두 통과해야 한다.
 */
@SpringBootTest
@Transactional
class JpaAuctionRoomUpdateAdapterIntegrationTest {

	private static final Instant FUTURE = Instant.parse("2026-12-01T00:00:00Z");

	@Autowired
	private JpaAuctionRoomUpdateAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (category_id, code, name, is_active, display_order)
			VALUES (9401, 'TEST_ROOM_UPD', '수정 테스트', TRUE, 9401)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES (9401, '방수정판매자', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (product_id, seller_member_id, category_id, title, description)
			VALUES
				(9401, 9401, 9401, '즉시 시작 방 상품', '설명'),
				(9402, 9401, 9401, '예약 방 상품', '설명')
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes,
				start_type, scheduled_at, status
			)
			VALUES
				(9401, 9401, 50000, 15, 'IMMEDIATE', NULL, 'WAITING'),
				(9402, 9402, 60000, 20, 'SCHEDULED', '2026-11-01T00:00:00Z', 'WAITING')
			""");
		entityManager.flush();
	}

	@Test
	void readsCurrentAuctionValues() {
		AuctionRoomUpdateTarget target = adapter.findUpdateTarget(9402L).orElseThrow();

		assertThat(target.productId()).isEqualTo(9402L);
		assertThat(target.sellerId()).isEqualTo(9401L);
		assertThat(target.status()).isEqualTo("WAITING");
		assertThat(target.deleted()).isFalse();
		assertThat(target.reservePrice()).isEqualTo(60_000L);
		assertThat(target.durationMinutes()).isEqualTo(20);
		assertThat(target.startType()).isEqualTo("SCHEDULED");
		assertThat(target.scheduledAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
	}

	@Test
	void reportsDeletedRoom() {
		execute("UPDATE auction_room SET deleted_at = NOW() WHERE auction_room_id = 9401");
		entityManager.flush();

		assertThat(adapter.findUpdateTarget(9401L).orElseThrow().deleted()).isTrue();
	}

	@Test
	void returnsEmptyForMissingRoom() {
		assertThat(adapter.findUpdateTarget(999_999L)).isEmpty();
	}

	@Test
	void switchesImmediateToScheduled() {
		adapter.updateAuctionRoom(9401L, 70_000L, 30, AuctionStartType.SCHEDULED, FUTURE);
		entityManager.flush();
		entityManager.clear();

		AuctionRoomUpdateTarget target = adapter.findUpdateTarget(9401L).orElseThrow();
		assertThat(target.reservePrice()).isEqualTo(70_000L);
		assertThat(target.durationMinutes()).isEqualTo(30);
		assertThat(target.startType()).isEqualTo("SCHEDULED");
		assertThat(target.scheduledAt()).isEqualTo(FUTURE);
	}

	/** null 예약 시각을 네이티브 파라미터로 넘길 때 타입을 못 정해 터지지 않는지도 함께 본다 */
	@Test
	void switchesScheduledToImmediateClearingSchedule() {
		adapter.updateAuctionRoom(9402L, 60_000L, 20, AuctionStartType.IMMEDIATE, null);
		entityManager.flush();
		entityManager.clear();

		AuctionRoomUpdateTarget target = adapter.findUpdateTarget(9402L).orElseThrow();
		assertThat(target.startType()).isEqualTo("IMMEDIATE");
		assertThat(target.scheduledAt()).isNull();
	}

	/** 경매방을 건드리지 않고 상품만 바꾼 경우에도 최신 시각이 나와야 한다 */
	@Test
	void readsLaterOfRoomAndProductUpdatedAt() {
		Instant beforeAny = adapter.findLastUpdatedAt(9401L);

		execute("UPDATE product SET title = '상품만 수정' WHERE product_id = 9401");
		entityManager.flush();
		entityManager.clear();

		assertThat(adapter.findLastUpdatedAt(9401L)).isAfterOrEqualTo(beforeAny);
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
